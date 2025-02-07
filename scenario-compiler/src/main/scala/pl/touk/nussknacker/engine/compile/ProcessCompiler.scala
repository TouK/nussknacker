package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.FragmentValidator.validateUniqueFragmentOutputNames
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph.part.{PotentiallyStartPart, TypedEnd}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledProcessParts, part}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{Source => _, _}
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.end.NormalEnd
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
    protected val customProcessValidator: CustomProcessValidator
) extends ProcessCompilerBase
    with ProcessValidator {

  override def withLabelsDictTyper: ProcessCompiler =
    new ProcessCompiler(
      classLoader,
      sub.withLabelsDictTyper,
      globalVariablesPreparer,
      nodeCompiler.withLabelsDictTyper,
      customProcessValidator
    )

  override def compile(
      process: CanonicalProcess
  )(implicit jobData: JobData): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }

}

trait ProcessValidator extends LazyLogging {

  def validate(process: CanonicalProcess, isFragment: Boolean)(implicit jobData: JobData): CompilationResult[Unit] = {

    try {
      CompilationResult.map4(
        CompilationResult(IdValidator.validate(process, isFragment)),
        CompilationResult(validateWithCustomProcessValidators(process)),
        CompilationResult(validateUniqueFragmentOutputNames(process, isFragment)),
        compile(process).map(_ => ()): CompilationResult[Unit]
      )((_, _, _, _) => { () })
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Unexpected error during compilation of ${process.name}", e)
        CompilationResult(Invalid(NonEmptyList.of(FatalUnknownError(e.getMessage))))
    }
  }

  private def validateWithCustomProcessValidators(
      process: CanonicalProcess
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    customProcessValidator.validate(process)
  }

  def withLabelsDictTyper: ProcessValidator

  protected def compile(process: CanonicalProcess)(implicit jobData: JobData): CompilationResult[_]

  protected def customProcessValidator: CustomProcessValidator

}

protected trait ProcessCompilerBase {

  protected def sub: PartSubGraphCompiler

  protected def classLoader: ClassLoader

  protected def nodeCompiler: NodeCompiler

  protected def globalVariablesPreparer: GlobalVariablesPreparer

  protected def compile(
      process: CanonicalProcess
  )(implicit jobData: JobData): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      val compilationResultWithArtificial =
        ProcessCanonizer.uncanonizeArtificial(process).map(ProcessSplitter.split).map(compile)
      compilationResultWithArtificial.extract
    }
  }

  private def contextWithOnlyGlobalVariables(implicit jobData: JobData): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)

  private def compile(
      splittedProcess: SplittedProcess
  )(implicit jobData: JobData): CompilationResult[CompiledProcessParts] =
    CompilationResult.map2(
      CompilationResult(findDuplicates(splittedProcess.sources).toValidatedNel),
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
  )(implicit jobData: JobData): CompilationResult[NonEmptyList[PotentiallyStartPart]] = {
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

  private def findDuplicates(parts: NonEmptyList[SourcePart]): Validated[ProcessCompilationError, Unit] = {
    val allNodes = NodesCollector.collectNodesInAllParts(parts)
    val duplicatedIds =
      allNodes.map(_.id).groupBy(identity).collect {
        case (id, grouped) if grouped.size > 1 =>
          id
      }
    if (duplicatedIds.isEmpty)
      valid(())
    else
      invalid(DuplicatedNodeIds(duplicatedIds.toSet))
  }

  private def compile(source: SourcePart, branchEndContexts: BranchEndContexts)(
      implicit jobData: JobData
  ): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    implicit val nodeId: NodeId = new NodeId(source.id)

    source match {
      case SourcePart(splittednode.SourceNode(sourceData: SourceNodeData, _), _, _) =>
        compileSourcePart(source, sourceData)
      case SourcePart(srcNode @ splittednode.SourceNode(data: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        compileCustomNodePart(source, node, data, Right(branchEndContexts))
    }

  }

  private def compileParts(parts: List[SubsequentPart], ctx: Map[String, ValidationContext])(
      implicit jobData: JobData
  ): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts
      .map(p =>
        ctx
          .get(p.id)
          .map(compileSubsequentPart(p, _))
          .getOrElse(CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id)))))
      )
      .sequence
  }

  private def compileSubsequentPart(part: SubsequentPart, ctx: ValidationContext)(
      implicit jobData: JobData
  ): CompilationResult[compiledgraph.part.SubsequentPart] = {
    implicit val nodeId: NodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        compileSinkPart(node, ctx)
      case CustomNodePart(node @ splittednode.EndingNode(data), _, _) =>
        compileEndingCustomNodePart(node, data, ctx)
      case CustomNodePart(node @ splittednode.OneOutputSubsequentNode(data, _), _, _) =>
        compileCustomNodePart(part, node, data, Left(ctx))
    }
  }

  def compileSourcePart(
      part: SourcePart,
      sourceData: SourceNodeData
  )(implicit nodeId: NodeId, jobData: JobData): CompilationResult[compiledgraph.part.SourcePart] = {
    val NodeCompilationResult(typingInfo, parameters, initialCtx, compiledSource, _) =
      nodeCompiler.compileSource(sourceData)

    val validatedSource = sub.validate(part.node, initialCtx.valueOr(_ => contextWithOnlyGlobalVariables))
    val typesForParts   = validatedSource.typing.mapValuesNow(_.inputValidationContext)
    val nodeTypingInfo  = Map(part.id -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo, parameters))

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
        part.ends.map(e => TypedEnd(e, typesForParts(e.nodeId)))
      )
    }
  }

  def compileSinkPart(
      node: EndingNode[Sink],
      ctx: ValidationContext
  )(implicit jobData: JobData, nodeId: NodeId): CompilationResult[part.SinkPart] = {
    val NodeCompilationResult(typingInfo, parameters, _, compiledSink, _) = nodeCompiler.compileSink(node.data, ctx)
    val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo, parameters))
    CompilationResult.map2(sub.validate(node, ctx), CompilationResult(nodeTypingInfo, compiledSink))((_, obj) =>
      compiledgraph.part.SinkPart(obj, node, ctx, ctx)
    )
  }

  def compileEndingCustomNodePart(
      node: splittednode.EndingNode[CustomNode],
      data: CustomNodeData,
      ctx: ValidationContext
  )(implicit jobData: JobData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
    val NodeCompilationResult(typingInfo, parameters, validatedNextCtx, compiledNode, _) =
      nodeCompiler.compileCustomNodeObject(data, Left(ctx), ending = true)
    val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo, parameters))

    CompilationResult
      .map2(
        CompilationResult(nodeTypingInfo, compiledNode),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, nextCtx) =>
        compiledgraph.part.CustomNodePart(
          nodeInvoker,
          node,
          ctx,
          nextCtx,
          List.empty,
          List(TypedEnd(NormalEnd(node.id), ctx))
        )
      }
      .distinctErrors
  }

  def compileCustomNodePart(
      part: ProcessPart,
      node: splittednode.OneOutputNode[CustomNodeData],
      data: CustomNodeData,
      ctx: Either[ValidationContext, BranchEndContexts]
  )(implicit jobData: JobData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
    val NodeCompilationResult(typingInfo, parameters, validatedNextCtx, compiledNode, _) =
      nodeCompiler.compileCustomNodeObject(data, ctx.map(_.contextsForJoin(data.id)), ending = false)

    val nextPartsValidation =
      sub.validate(node, validatedNextCtx.valueOr(_ => ctx.left.getOrElse(contextWithOnlyGlobalVariables)))
    val typesForParts = nextPartsValidation.typing.mapValuesNow(_.inputValidationContext)
    val nodeTypingInfo = Map(
      node.id -> NodeTypingInfo(ctx.left.getOrElse(contextWithOnlyGlobalVariables), typingInfo, parameters)
    )

    CompilationResult
      .map4(
        CompilationResult(nodeTypingInfo, compiledNode),
        nextPartsValidation,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, _, nextPartsCompiled, nextCtx) =>
        // TODO: what should be passed for joins here?
        compiledgraph.part.CustomNodePart(
          nodeInvoker,
          node,
          ctx.left.getOrElse(ValidationContext.empty),
          nextCtx,
          nextPartsCompiled,
          part.ends.map(e => TypedEnd(e, typesForParts(e.nodeId)))
        )
      }
      .distinctErrors
  }

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
      new FragmentParametersDefinitionExtractor(classLoader, definitionWithTypes.classDefinitions),
      expressionCompiler,
      classLoader,
      Seq.empty,
      PreventInvocationCollector,
      ComponentUseCase.Validation,
      nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
    )
    val sub = new PartSubGraphCompiler(nodeCompiler)
    new ProcessCompiler(
      classLoader,
      sub,
      globalVariablesPreparer,
      nodeCompiler,
      customProcessValidator
    )
  }

}
