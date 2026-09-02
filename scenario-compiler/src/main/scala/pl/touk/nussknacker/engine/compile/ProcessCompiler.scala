package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.component.{ComponentOutput, NodesDeploymentData, SupportsMultipleOutputs}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.{MissingSinkHandler, ProcessCanonizer}
import pl.touk.nussknacker.engine.compile.FragmentValidator.validateUniqueFragmentOutputNames
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  LazyParameterCreationStrategy,
  MultipleInputBranchesNodeInputValidationContext,
  NodeCompiler,
  NodeInputValidationContext,
  SingleInputNodeInputValidationContext
}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph.{part, CompiledProcessParts}
import pl.touk.nussknacker.engine.compiledgraph.part.{PotentiallyStartPart, TypedEnd}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{Source => _, _}
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.EndingNode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.util.control.NonFatal

class ProcessCompiler(
    protected val classLoader: ClassLoader,
    protected val sub: PartSubGraphCompiler,
    protected val globalVariablesPreparer: GlobalVariablesPreparer,
    protected val nodeCompiler: NodeCompiler,
    protected val customProcessValidator: CustomProcessValidator,
    protected val allowEndingScenarioWithoutSink: Boolean,
) extends ProcessCompilerBase
    with ProcessValidator {

  override def withLabelsDictTyper: ProcessCompiler =
    new ProcessCompiler(
      classLoader,
      sub.withLabelsDictTyper,
      globalVariablesPreparer,
      nodeCompiler.withLabelsDictTyper,
      customProcessValidator,
      allowEndingScenarioWithoutSink
    )

  // ProcessCompiler does not compile fragment, you must resolve it with ScenarioResolver before!
  override def compile(
      process: CanonicalProcess
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }

}

trait ProcessValidator extends LazyLogging {

  def validate(process: CanonicalProcess, isFragment: Boolean)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[Unit] = {

    try {
      CompilationResult.map4(
        CompilationResult(NameValidator.validate(process, isFragment)),
        CompilationResult(validateWithCustomProcessValidators(process)),
        CompilationResult(validateUniqueFragmentOutputNames(process, isFragment)),
        compile(process).map(_ => ()): CompilationResult[Unit]
      )((_, _, _, _) => { () })
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Unexpected error during compilation of ${process.name}", e)
        CompilationResult(Invalid(NonEmptyList.of(FatalUnknownError(e.getMessage, nodeId = None))))
    }
  }

  private def validateWithCustomProcessValidators(
      process: CanonicalProcess
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    customProcessValidator.validate(process)
  }

  def withLabelsDictTyper: ProcessValidator

  protected def compile(process: CanonicalProcess)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[_]

  protected def customProcessValidator: CustomProcessValidator

}

protected trait ProcessCompilerBase {

  protected def sub: PartSubGraphCompiler

  protected def classLoader: ClassLoader

  protected def nodeCompiler: NodeCompiler

  protected def globalVariablesPreparer: GlobalVariablesPreparer

  protected def allowEndingScenarioWithoutSink: Boolean

  protected def compile(
      process: CanonicalProcess
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withContextClassLoader(classLoader) {
      val compilationResultWithArtificial =
        ProcessCanonizer
          .uncanonizeArtificial(process, missingSinkHandler)
          .map(ProcessSplitter.split)
          .map(compile)
      compilationResultWithArtificial.extract
    }
  }

  private def missingSinkHandler(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): MissingSinkHandler = {
    if (allowEndingScenarioWithoutSink) MissingSinkHandler.AllowMissingSinkHandler
    else MissingSinkHandler.DoNotAllowMissingSinkHandler
  }

  private def contextWithOnlyGlobalVariables(implicit jobData: JobData): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)

  private def compile(
      splittedProcess: SplittedProcess
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[CompiledProcessParts] =
    CompilationResult.map2(
      CompilationResult(findDuplicates(splittedProcess.sources)),
      compileSources(splittedProcess.sources)
    ) { (_, sources) =>
      CompiledProcessParts(sources)
    }

  /*
    We need to sort SourceParts to know types of variables in branches for joins. See comment in PartSort
    In the future we'll probably move to direct representation of process as graph and this will no longer be needed
   */
  private def compileSources(
      sources: NonEmptyList[SourcePart]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[NonEmptyList[PotentiallyStartPart]] = {
    val zeroAcc = (CompilationResult(Valid(List[PotentiallyStartPart]())), new BranchEndContexts(Nil))
    // we use fold here (and not map/sequence), because we can compile part which starts from Join only when we
    // know compilation results (stored in BranchEndContexts) of all branches that end in this join
    val (result, _) =
      PartSort.sort(sources.toList).foldLeft(zeroAcc) { case ((resultSoFar, branchContexts), nextSourcePart) =>
        val compiledPart = compile(nextSourcePart, branchContexts)
        // we don't use andThen on CompilationResult, since we don't want to stop if there are errors in part
        val nextResult = CompilationResult.map2(resultSoFar, compiledPart)(_ :+ _)
        (nextResult, branchContexts.addPart(nextSourcePart, compiledPart))
      }
    result.map(NonEmptyList.fromListUnsafe)
  }

  private def findDuplicates(parts: NonEmptyList[SourcePart]): ValidatedNel[ProcessCompilationError, Unit] = {
    val allNodes   = NodesCollector.collectNodesInAllParts(parts)
    val duplicates = allNodes.groupBy(_.id).filter { case (_, nodes) => nodes.size > 1 }

    // Duplicated branch ends mean a join is reached twice under one branch key (the source node's name) - typically
    // one node through several of its outputs. Reported separately, because DuplicatedNodeIds would name the
    // artificial `$edge-...` node the user never sees.
    val (duplicatedBranchEnds, duplicatedIds) = duplicates.partitionMap { case (id, nodes) =>
      nodes.head.data match {
        case BranchEndData(definition) => Left(definition)
        case _                         => Right(id -> nodes.map(_.data.name).toSet)
      }
    }

    val sameJoinErrors = duplicatedBranchEnds.toList.map { definition =>
      MultipleOutputsToSameJoin(branchSourceNodeIds(definition, allNodes))
    }

    val duplicatedIdErrors = Option.when(duplicatedIds.nonEmpty)(DuplicatedNodeIds(duplicatedIds.toMap)).toList

    NonEmptyList.fromList(sameJoinErrors ++ duplicatedIdErrors).map(Invalid(_)).getOrElse(valid(()))
  }

  /**
    * The canonical branchId (`BranchEndDefinition.id`) is the source node's *name* (see the Join case in
    * CanonicalProcessConverter), while the designer attaches errors by node *id*, so the name has to be resolved back.
    * It resolves to nothing for a resolved fragment, whose branchId FragmentResolver leaves unprefixed, so the
    * fallback is `joinId` - the only id the graph is still guaranteed to contain.
    */
  private def branchSourceNodeIds(
      definition: BranchEndDefinition,
      allNodes: List[splittednode.SplittedNode[_ <: NodeData]]
  ): Set[NodeId] = {
    val matchingIds = allNodes
      .map(_.data)
      .flatMap {
        case _: BranchEndData                         => Nil
        case data if data.name.value == definition.id => List(data.id)
        case _                                        => Nil
      }
      .toSet

    if (matchingIds.nonEmpty) {
      matchingIds
    } else {
      Set(NodeId(definition.joinId))
    }
  }

  private def compile(source: SourcePart, branchEndContexts: BranchEndContexts)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    source match {
      case SourcePart(splittednode.SourceNode(sourceData: SourceNodeData, _), _, _) =>
        compileSourcePart(source, sourceData)
      case SourcePart(srcNode @ splittednode.SourceNode(_: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        compileCustomNodePart(source, node, Right(branchEndContexts))
    }

  }

  private def compileParts(parts: List[SubsequentPart], partInputContexts: Map[String, ValidationContext])(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts
      .map(p =>
        partInputContexts
          .get(p.id.value)
          .map(compileSubsequentPart(p, _))
          .getOrElse(
            CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id, p.node.data.name))))
          )
      )
      .sequence
  }

  private def compileSubsequentPart(part: SubsequentPart, partInputContext: ValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.SubsequentPart] = {
    part match {
      case SinkPart(node) =>
        compileSinkPart(node, partInputContext)
      case SingleOutputCustomNodePart(node @ splittednode.EndingNode(_), _, _) =>
        compileEndingCustomNodePart(node, partInputContext)
      case customNodePart: CustomNodePart =>
        compileCustomNodePart(customNodePart, customNodePart.node, Left(partInputContext))
    }
  }

  private def compileSourcePart(
      part: SourcePart,
      sourceData: SourceNodeData
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.SourcePart] = {
    import scenarioCompilationDependencies._
    val NodeCompilationResult(typingInfo, parameters, initialCtx, compiledSource, _) =
      nodeCompiler.compileSource(sourceData)

    val validatedSource = sub.validate(
      part.node,
      SingleInputNodeInputValidationContext(initialCtx.valueOr(_ => contextWithOnlyGlobalVariables))
    )
    val typesForParts = validatedSource.typing.mapValuesNow(_.inputValidationContext)
    val nodeTypingInfo = Map(
      part.id.value -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo, parameters, initialCtx.toOption)
    )

    CompilationResult.map4(
      validatedSource,
      compileParts(part.nextParts, typesForParts),
      CompilationResult(initialCtx),
      CompilationResult(nodeTypingInfo, compiledSource)
    ) { (_, nextParts, ctx, obj) =>
      compiledgraph.part.SourcePart(
        obj,
        splittednode.SourceNode(sourceData, part.node.next),
        ctx,
        nextParts,
        part.ends.map(e => TypedEnd(e, typesForParts.getOrElse(e.nodeId.value, ValidationContext.empty)))
      )
    }
  }

  private def compileSinkPart(
      node: EndingNode[Sink],
      ctx: ValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[part.SinkPart] = {
    val NodeCompilationResult(typingInfo, parameters, outputCtx, compiledSink, _) =
      nodeCompiler.compileSink(node.data, SingleInputNodeInputValidationContext(ctx))
    val nodeTypingInfo = Map(node.id.value -> NodeTypingInfo(ctx, typingInfo, parameters, outputCtx.toOption))
    CompilationResult.map2(
      sub.validate(node, SingleInputNodeInputValidationContext(ctx)),
      CompilationResult(nodeTypingInfo, compiledSink)
    )((_, obj) => compiledgraph.part.SinkPart(obj, node, ctx, ctx))
  }

  private def compileEndingCustomNodePart(
      node: splittednode.EndingNode[CustomNode],
      ctx: ValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.CustomNodePart] = {
    val NodeCompilationResult(typingInfo, parameters, validatedNextCtx, compiledNode, _) =
      nodeCompiler.compileCustomNodeObject(node, SingleInputNodeInputValidationContext(ctx))
    val nodeTypingInfo = Map(node.id.value -> NodeTypingInfo(ctx, typingInfo, parameters, validatedNextCtx.toOption))

    CompilationResult
      .map2(
        CompilationResult(nodeTypingInfo, compiledNode),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, nextCtx) =>
        compiledgraph.part.CustomNodePart(
          nodeInvoker,
          ctx,
          nextCtx,
          NonEmptyList.one(
            compiledgraph.part.CompiledOutput(
              declaredOutputs(node.data.nodeType, node.id).head,
              node,
              List.empty,
              List(TypedEnd(NormalEnd(node.id), ctx))
            )
          )
        )
      }
      .distinctErrors
  }

  private def compileCustomNodePart(
      part: ProcessPart,
      node: splittednode.SplittedNode[CustomNodeData],
      ctx: Either[ValidationContext, BranchEndContexts]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.CustomNodePart] =
    part match {
      // Joins never carry additional outputs (guarded at definition load), so they always take the embedded shape.
      case multiOutputPart: MultiOutputCustomNodePart => compileMultiOutputCustomNodePart(multiOutputPart, ctx)
      case _                                          => compileEmbeddedOutputCustomNodePart(part, node, ctx)
    }

  /**
    * The named-outputs shape: every wired continuation, the main one included, compiles and validates through its own
    * output entry, so no chain hangs off the spine. Validating the spine would only add a second typing entry for the
    * custom node, keyed to the after-node context, and the typing merge would warn about conflicting contexts on
    * every compilation.
    */
  private def compileMultiOutputCustomNodePart(
      part: MultiOutputCustomNodePart,
      ctx: Either[ValidationContext, BranchEndContexts]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.CustomNodePart] = {
    import scenarioCompilationDependencies._

    val node     = part.node
    val nodeData = node.data

    // With additional outputs connected the node does not end the scenario, even if its main output is unwired.
    val NodeCompilationResult(typingInfo, parameters, validatedNextCtx, compiledNode, _) =
      nodeCompiler.compileCustomNodeObject(nodeData, customNodeInputValidationContext(node, ctx), Some(false))

    val nextPartInputValidationContext = customNodeNextPartInputValidationContext(validatedNextCtx, ctx)

    // Each output re-uses the node's data with its own continuation and validates against the same after-node context.
    val outputData = part.outputs.toList.map { output =>
      val outputNode          = splittednode.OneOutputSubsequentNode(nodeData, output.next)
      val outputValidation    = sub.validate(outputNode, nextPartInputValidationContext)
      val outputTypesForParts = outputValidation.typing.mapValuesNow(_.inputValidationContext)
      (output, outputNode, outputValidation, outputTypesForParts)
    }

    val declaredOutputsForNode = nodeCompiler.declaredOutputs(nodeData.nodeType)
    val outputNames            = outputData.map { case (output, _, _, _) => output.name }
    val outputNamesSet         = outputNames.toSet

    val outputNameValidation: CompilationResult[Unit] = {
      val unknownOutputNames  = declaredOutputsForNode.fold(Set.empty[String])(_.undeclaredAmong(outputNamesSet))
      val unknownOutputErrors = unknownOutputNames.toList.sorted.map(UnknownCustomNodeOutput(_, Set(node.id)))
      CompilationResult(NonEmptyList.fromList(unknownOutputErrors).map(Invalid(_)).getOrElse(valid(())))
    }

    val duplicateOutputNamesValidation: CompilationResult[Unit] = {
      val duplicatedNames = outputNames.groupBy(identity).filter(_._2.sizeIs > 1).keys.toList.sorted
      if (duplicatedNames.isEmpty) CompilationResult(Valid(()))
      else
        CompilationResult(Invalid(NonEmptyList.one(DuplicateCustomNodeOutputNames(duplicatedNames, Set(node.id)))))
    }

    def declared: NonEmptyList[ComponentOutput] =
      declaredOutputsForNode
        .map(_.outputs)
        .getOrElse(missingComponent(nodeData.nodeType, node.id))

    // Looked up among the declared instances rather than built from the edge; `outputNameValidation` guards the miss.
    def declaredOutput(outputName: String): ComponentOutput =
      declared
        .find(_.name == outputName)
        .getOrElse(
          throw new IllegalStateException(s"Output '$outputName' not declared by '${nodeData.nodeType}'")
        )

    val outputsCompilation: CompilationResult[List[compiledgraph.part.CompiledOutput]] =
      outputData.map { case (output, outputNode, outputValidation, outputTypesForParts) =>
        CompilationResult.map4(
          CompilationResult(compiledNode),
          outputNameValidation,
          outputValidation,
          compileParts(output.nextParts, outputTypesForParts)
        ) { (_, _, _, outputNextPartsCompiled) =>
          compiledgraph.part.CompiledOutput(
            declaredOutput(output.name),
            outputNode,
            outputNextPartsCompiled,
            output.ends.map(e => TypedEnd(e, outputTypesForParts.getOrElse(e.nodeId.value, ValidationContext.empty)))
          )
        }
      }.sequence

    val nodeTypingInfo = Map(
      node.id.value -> NodeTypingInfo(
        ctx.left.getOrElse(contextWithOnlyGlobalVariables),
        typingInfo,
        parameters,
        validatedNextCtx.toOption
      )
    )

    // Wired additional outputs only work when the returned implementation can route them - reported here rather
    // than at job registration, where it would surface only on deployment. A main-only wiring runs on any
    // implementation.
    val additionalOutputsSupportValidation: CompilationResult[Unit] = compiledNode match {
      case _ if declaredOutputsForNode.forall(_.wiredAdditionalAmong(outputNamesSet).isEmpty) =>
        CompilationResult(Valid(()))
      case Valid(_: SupportsMultipleOutputs) => CompilationResult(Valid(()))
      case Valid(_) =>
        CompilationResult(
          Invalid(
            NonEmptyList.one(
              CustomNodeError(
                node.id,
                "Additional outputs of this component are not supported on this engine - disconnect them.",
                None
              )
            )
          )
        )
      // A node that failed to compile for another reason gets no second error here.
      case Invalid(_) => CompilationResult(Valid(()))
    }

    val outputsValidation: CompilationResult[Unit] =
      CompilationResult.map3(outputNameValidation, duplicateOutputNamesValidation, additionalOutputsSupportValidation)(
        (_, _, _) => ()
      )

    // An unwired main gets a DeadEnd stand-in entry - the node does not end the scenario.
    def mainOutputWithoutOwnEntry: compiledgraph.part.CompiledOutput =
      compiledgraph.part.CompiledOutput(
        declared.head,
        splittednode.OneOutputSubsequentNode(nodeData, None),
        List.empty,
        List(TypedEnd(DeadEnd(node.id), nextPartInputValidationContext.validationContext))
      )

    CompilationResult
      .map4(
        CompilationResult(nodeTypingInfo, compiledNode),
        CompilationResult(validatedNextCtx),
        outputsCompilation,
        outputsValidation
      ) { (nodeInvoker, nextCtx, compiledOutputs, _) =>
        val mainOutput = compiledOutputs.find(_.output == declared.head).getOrElse(mainOutputWithoutOwnEntry)
        compiledgraph.part.CustomNodePart(
          nodeInvoker,
          ctx.left.getOrElse(ValidationContext.empty),
          nextCtx,
          NonEmptyList(
            mainOutput,
            // Ordered by the declaration, not by the compiled outputs: those follow the scenario's edge order.
            declared.tail.flatMap(output => compiledOutputs.find(_.output == output))
          )
        )
      }
      .distinctErrors
  }

  /**
    * The embedded shape: the main chain hangs directly off the node, as the single-output path has always compiled.
    * Also the join shape - a join never carries additional outputs.
    */
  private def compileEmbeddedOutputCustomNodePart(
      part: ProcessPart,
      node: splittednode.SplittedNode[CustomNodeData],
      ctx: Either[ValidationContext, BranchEndContexts]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.part.CustomNodePart] = {
    import scenarioCompilationDependencies._

    val NodeCompilationResult(typingInfo, parameters, validatedNextCtx, compiledNode, _) =
      nodeCompiler.compileCustomNodeObject(node.data, customNodeInputValidationContext(node, ctx), Some(node.isEnding))

    val nextPartsValidation =
      sub.validate(node, customNodeNextPartInputValidationContext(validatedNextCtx, ctx))
    val typesForParts = nextPartsValidation.typing.mapValuesNow(_.inputValidationContext)

    val nodeTypingInfo = Map(
      node.id.value -> NodeTypingInfo(
        ctx.left.getOrElse(contextWithOnlyGlobalVariables),
        typingInfo,
        parameters,
        validatedNextCtx.toOption
      )
    )

    CompilationResult
      .map4(
        CompilationResult(nodeTypingInfo, compiledNode),
        nextPartsValidation,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, _, mainNextPartsCompiled, nextCtx) =>
        compiledgraph.part.CustomNodePart(
          nodeInvoker,
          // TODO: what should be passed for joins here?
          ctx.left.getOrElse(ValidationContext.empty),
          nextCtx,
          NonEmptyList.one(
            compiledgraph.part.CompiledOutput(
              declaredOutputs(node.data.nodeType, node.id).head,
              node,
              mainNextPartsCompiled,
              part.ends.map(e => TypedEnd(e, typesForParts.getOrElse(e.nodeId.value, ValidationContext.empty)))
            )
          )
        )
      }
      .distinctErrors
  }

  private def customNodeInputValidationContext(
      node: splittednode.SplittedNode[CustomNodeData],
      ctx: Either[ValidationContext, BranchEndContexts]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeInputValidationContext = {
    import scenarioCompilationDependencies._
    ctx match {
      case Left(singleContext) => SingleInputNodeInputValidationContext(singleContext)
      case Right(branchEndContexts) =>
        MultipleInputBranchesNodeInputValidationContext(
          branchEndContexts.contextsForJoin(node.id.value),
          contextWithOnlyGlobalVariables
        )
    }
  }

  private def customNodeNextPartInputValidationContext(
      validatedNextCtx: ValidatedNel[ProcessCompilationError, ValidationContext],
      ctx: Either[ValidationContext, BranchEndContexts]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): SingleInputNodeInputValidationContext = {
    import scenarioCompilationDependencies._
    validatedNextCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse {
      ctx match {
        case Left(singleContext) => SingleInputNodeInputValidationContext(singleContext)
        case Right(_)            => SingleInputNodeInputValidationContext(contextWithOnlyGlobalVariables)
      }
    }
  }

  /**
    * Call only from inside a `CompilationResult` mapping lambda: those run once every input is `Valid`, which implies
    * the component exists. A miss here is an invariant break in the compiler, not a fixable scenario error.
    */
  private def declaredOutputs(nodeType: String, nodeId: NodeId): NonEmptyList[ComponentOutput] =
    nodeCompiler.declaredOutputs(nodeType).map(_.outputs).getOrElse(missingComponent(nodeType, nodeId))

  private def missingComponent(nodeType: String, nodeId: NodeId): Nothing =
    throw new IllegalStateException(s"No custom component '$nodeType' for compiled node $nodeId")

  private class BranchEndContexts(joinIdBranchIdContexts: List[(String, (String, ValidationContext))]) {

    def addPart(part: ProcessPart, result: CompilationResult[_]): BranchEndContexts = {
      val newData =
        NodesCollector.collectNodesInAllParts(part).collect { case splittednode.EndingNode(BranchEndData(definition)) =>
          definition.joinId -> (definition.id -> result.typing
            .apply(definition.artificialNodeId)
            .inputValidationContext)
        }
      new BranchEndContexts(joinIdBranchIdContexts ++ newData)
    }

    def contextsForJoin(joinId: String): Map[String, ValidationContext] = joinIdBranchIdContexts.collect {
      case (`joinId`, data) => data
    }.toMap

  }

}

object ProcessValidator {

  def default(modelData: ModelData): ProcessValidator = {
    default(
      modelData.modelDefinitionWithClasses,
      modelData.designerDictServices.dictRegistry,
      modelData.customProcessValidator,
      modelData.modelClassLoader
    )
  }

  def default(
      definitionWithTypes: ModelDefinitionWithClasses,
      dictRegistry: DictRegistry,
      customProcessValidator: CustomProcessValidator,
      classLoader: ClassLoader = getClass.getClassLoader
  ): ProcessValidator = {
    import definitionWithTypes.modelDefinition

    val globalVariablesPreparer = GlobalVariablesPreparer(modelDefinition.expressionConfig)
    val expressionEvaluator     = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)

    val expressionCompiler = ExpressionCompiler.withoutOptimization(
      classLoader,
      dictRegistry,
      modelDefinition.expressionConfig,
      definitionWithTypes.classDefinitions,
      expressionEvaluator
    )

    val nodeCompiler = new NodeCompiler(
      modelDefinition,
      new FragmentParametersDefinitionExtractor(
        classLoader,
        definitionWithTypes.classDefinitions,
        modelDefinition.globalParametersConfig
      ),
      expressionCompiler,
      classLoader,
      Seq.empty,
      PreventInvocationCollector,
      RuntimeMode.Live,
      NodesDeploymentData.empty,
      nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default,
    )
    val sub = new PartSubGraphCompiler(nodeCompiler)
    new ProcessCompiler(
      classLoader,
      sub,
      globalVariablesPreparer,
      nodeCompiler,
      customProcessValidator,
      modelDefinition.allowEndingScenarioWithoutSink
    )
  }

}
