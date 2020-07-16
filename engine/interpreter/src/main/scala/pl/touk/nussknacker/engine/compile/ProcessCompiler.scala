package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import cats.syntax.apply._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{JoinGenericNodeTransformation, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.nodevalidation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledProcessParts, part}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.compiledgraph.part.PotentiallyStartPart
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{Sink, Source => _, _}
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam, node}
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.end.NormalEnd
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.EndingNode
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.Typeable
import shapeless.syntax.typeable._

import scala.util.control.NonFatal

class ProcessCompiler(protected val classLoader: ClassLoader,
                      protected val sub: PartSubGraphCompiler,
                      protected val definitions: ProcessDefinition[ObjectWithMethodDef],
                      // compiler for object (source, sink, custom transformer) parameters
                      protected val objectParametersExpressionCompiler: ExpressionCompiler
                     ) extends ProcessCompilerBase with ProcessValidator {

  //FIXME: should it be here?
  private val expressionEvaluator =
    ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(expressionConfig))

  override def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }

  override def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ProcessCompiler =
    new ProcessCompiler(classLoader, sub.withExpressionParsers(modify), definitions, objectParametersExpressionCompiler.withExpressionParsers(modify))

  override protected def factory: ProcessObjectFactory = new ProcessObjectFactory(expressionEvaluator)

}

trait ProcessValidator extends LazyLogging {

  def validate(canonical: CanonicalProcess): CompilationResult[Unit] = {
    ProcessCanonizer.uncanonizeArtificial(canonical).map(validate).extract
  }

  def validate(process: EspProcess): CompilationResult[Unit] = {
    try {
      compile(process).map(_ => Unit)
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Unexpected error during compilation of ${process.id}", e)
        CompilationResult(Invalid(NonEmptyList.of(FatalUnknownError(e.getMessage))))
    }
  }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ProcessValidator

  protected def compile(process: EspProcess): CompilationResult[_]

}

protected trait ProcessCompilerBase {

  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  protected val customStreamTransformers: Map[String, (ObjectWithMethodDef, ProcessDefinitionExtractor.CustomTransformerAdditionalData)] = definitions.customStreamTransformers
  protected val expressionConfig: ProcessDefinitionExtractor.ExpressionDefinition[ObjectWithMethodDef] = definitions.expressionConfig
  private val syntax = ValidatedSyntax[ProcessCompilationError]

  protected def definitions: ProcessDefinition[ObjectWithMethodDef]

  protected def sourceFactories: Map[String, ObjectWithMethodDef] = definitions.sourceFactories

  protected def sinkFactories: Map[String, (ObjectWithMethodDef, ProcessDefinitionExtractor.SinkAdditionalData)] = definitions.sinkFactories

  import syntax._

  protected def exceptionHandlerFactory: ObjectWithMethodDef = definitions.exceptionHandlerFactory

  protected def sub: PartSubGraphCompiler

  protected def classLoader: ClassLoader

  protected def objectParametersExpressionCompiler: ExpressionCompiler

  protected def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      compile(ProcessSplitter.split(process))
    }
  }

  protected def factory: ProcessObjectFactory

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext
  = globalVariablesPreparer.emptyValidationContext(metaData)

  private def nodeValidator(implicit metaData: MetaData)
  = new GenericNodeTransformationValidator(objectParametersExpressionCompiler, expressionConfig)

  private def compile(splittedProcess: SplittedProcess): CompilationResult[CompiledProcessParts] = {
    implicit val metaData: MetaData = splittedProcess.metaData
    val (typingInfo, compiledExceptionHandler) = compile(splittedProcess.exceptionHandlerRef)
    val nodeTypingInfo = Map(NodeTypingInfo.ExceptionHandlerNodeId -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo))
    CompilationResult.map3(
      CompilationResult(findDuplicates(splittedProcess.sources).toValidatedNel),
      CompilationResult(nodeTypingInfo, compiledExceptionHandler),
      compileSources(splittedProcess.sources)
    ) { (_, exceptionHandler, sources) =>
      CompiledProcessParts(splittedProcess.metaData, exceptionHandler, sources)
    }
  }

  /*
    We need to sort SourceParts to know types of variables in branches for joins. See comment in PartSort
    In the future we'll probably move to direct representation of process as graph and this will no longer be needed
   */
  private def compileSources(sources: NonEmptyList[SourcePart])(implicit meta: MetaData): CompilationResult[NonEmptyList[PotentiallyStartPart]] = {
    val zeroAcc = (CompilationResult(Valid(List[PotentiallyStartPart]())), BranchEndContexts(Map()))
    //we use fold here (and not map/sequence), because we can compile part which starts from Join only when we
    //know compilation results (stored in BranchEndContexts) of all branches that end in this join
    val (result, _) = PartSort.sort(sources.toList).foldLeft(zeroAcc) { case ((resultSoFar, branchContexts), nextSourcePart) =>
      val compiledPart = compile(nextSourcePart, branchContexts)
      //we don't use andThen on CompilationResult, since we don't want to stop if there are errors in part
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
      valid(Unit)
    else
      invalid(DuplicatedNodeIds(duplicatedIds.toSet))
  }

  private def compile(ref: ExceptionHandlerRef)
                     (implicit metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, EspExceptionHandler]) = {
    implicit val nodeId: NodeId = NodeId(NodeTypingInfo.ExceptionHandlerNodeId)
    if (metaData.isSubprocess) {
      //FIXME: what should be here?
      (Map.empty, Valid(new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      }))
    } else {
      createProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters, List.empty, outputVariableNameOpt = None, Left(contextWithOnlyGlobalVariables))
    }
  }

  private def compile(source: SourcePart, branchEndContexts: BranchEndContexts)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    implicit val nodeId: NodeId = NodeId(source.id)

    source match {
      case SourcePart(node@splittednode.SourceNode(sourceData: SourceNodeData, _), _, _) =>
        SourceNodeCompiler.compileSourcePart(source, sourceData)
      case SourcePart(srcNode@splittednode.SourceNode(data: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        CustomNodeCompiler.compileCustomNodePart(source, node, data, Right(branchEndContexts))
    }

  }

  private def compileParts(parts: List[SubsequentPart], ctx: Map[String, ValidationContext])
                          (implicit metaData: MetaData): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts.map(p =>
      ctx.get(p.id).map(compileSubsequentPart(p, _)).getOrElse(CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id)))))
    ).sequence
  }

  private def compileSubsequentPart(part: SubsequentPart, ctx: ValidationContext)
                                   (implicit metaData: MetaData): CompilationResult[compiledgraph.part.SubsequentPart] = {
    implicit val nodeId: NodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        SinkNodeCompiler.compileSinkPart(node, ctx)
      case CustomNodePart(node@splittednode.EndingNode(data), _, _) =>
        CustomNodeCompiler.compileEndingCustomNodePart(node, data, ctx)
      case CustomNodePart(node@splittednode.OneOutputSubsequentNode(data, _), _, _) =>
        CustomNodeCompiler.compileCustomNodePart(part, node, data, Left(ctx))
    }
  }

  private def compileObjectWithTransformation[T](data: NodeData with WithParameters,
                                                 ctx: Either[ValidationContext, BranchEndContexts],
                                                 outputVar: Option[String],
                                                 nodeDefinition: ObjectWithMethodDef,
                                                 defaultCtxForCreatedObject: Option[T] => ValidatedNel[ProcessCompilationError, ValidationContext])
                                                (implicit metaData: MetaData, nodeId: NodeId):
  (Map[String, ExpressionTypingInfo], Validated[NonEmptyList[ProcessCompilationError], ValidationContext], ValidatedNel[ProcessCompilationError, T]) = {
    val branchParameters = data.cast[Join].map(_.branchParameters).getOrElse(List.empty)
    val parameters = data.parameters
    val generic = validateGenericTransformer(ctx, parameters, branchParameters, outputVar)
    if (generic.isDefinedAt(nodeDefinition)) {
      val afterValidation = generic(nodeDefinition).map {
        case TransformationResult(Nil, computedParameters, outputContext) =>
          val (typingInfo, validProcessObject) = createProcessObject[T](nodeDefinition, parameters,
            branchParameters, outputVar, ctx, Some(computedParameters))
          (typingInfo, outputContext, validProcessObject)
        case TransformationResult(h :: t, _, outputContext) =>
          //TODO: typing info here??
          (Map.empty[String, ExpressionTypingInfo], outputContext, Invalid(NonEmptyList(h, t)))
      }
      (afterValidation.map(_._1).valueOr(_ => Map.empty), afterValidation.map(_._2), afterValidation.andThen(_._3))
    } else {
      val (typingInfo, validProcessObject) = createProcessObject[T](nodeDefinition, parameters,
        branchParameters, outputVar, ctx)
      val nextCtx = validProcessObject.fold(_ => defaultCtxForCreatedObject(None), cNode =>
        contextAfterNode(data, cNode, ctx, (c: T) => defaultCtxForCreatedObject(Some(c)))
      )
      (typingInfo, nextCtx, validProcessObject)
    }
  }

  private def contextAfterNode[T](node: NodeData, cNode: T,
                                  validationContexts: Either[ValidationContext, BranchEndContexts],
                                  legacy: T => ValidatedNel[ProcessCompilationError, ValidationContext])
                                 (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val contextTransformationDefOpt = cNode.cast[AbstractContextTransformation].map(_.definition)
    (contextTransformationDefOpt, validationContexts) match {
      case (Some(transformation: ContextTransformationDef), Left(validationContext)) =>
        // copying global variables because custom transformation may override them -> todo in ValidationContext
        transformation.transform(validationContext).map(_.copy(globalVariables = validationContext.globalVariables))
      case (Some(transformation: JoinContextTransformationDef), Right(branchEndContexts)) =>
        // TODO JOIN: better error
        val joinNode = node.cast[Join].getOrElse(throw new IllegalArgumentException(s"Should be used join element in node ${nodeId.id}"))
        val contexts = joinNode.branchParameters
          .groupBy(_.branchId).keys.map(k => k -> branchEndContexts.contextForId(k)).toMap
        // copying global variables because custom transformation may override them -> todo in ValidationContext
        transformation.transform(contexts).map(_.copy(globalVariables = contextWithOnlyGlobalVariables.globalVariables))
      case (Some(transformation), ctx) =>
        Invalid(FatalUnknownError(s"Invalid ContextTransformation class $transformation for contexts: $ctx")).toValidatedNel
      case (None, _) =>
        legacy(cNode)
    }
  }

  private def returnType(nodeDefinition: ObjectWithMethodDef, obj: Any): Option[TypingResult] = {
    if (obj.isInstanceOf[ReturningType]) {
      Some(obj.asInstanceOf[ReturningType].returnType)
    } else if (nodeDefinition.hasNoReturn) {
      None
    } else {
      Some(nodeDefinition.returnType)
    }
  }

  private def validateGenericTransformer[T](ctx: Either[ValidationContext, BranchEndContexts],
                                            parameters: List[evaluatedparam.Parameter],
                                            branchParameters: List[BranchParameters], outputVar: Option[String])
                                           (implicit metaData: MetaData, nodeId: NodeId):
  PartialFunction[ObjectWithMethodDef, Validated[NonEmptyList[ProcessCompilationError], TransformationResult]] = {

    case nodeDefinition if nodeDefinition.obj.isInstanceOf[SingleInputGenericNodeTransformation[_]] && ctx.isLeft =>
      val transformer = nodeDefinition.obj.asInstanceOf[SingleInputGenericNodeTransformation[_]]
      nodeValidator.validateNode(transformer, parameters, branchParameters, outputVar)(ctx.left.get)
    case nodeDefinition if nodeDefinition.obj.isInstanceOf[JoinGenericNodeTransformation[_]] && ctx.isRight =>
      val transformer = nodeDefinition.obj.asInstanceOf[JoinGenericNodeTransformation[_]]
      nodeValidator.validateNode(transformer, parameters, branchParameters, outputVar)(ctx.right.get.contexts)
  }

  private def createProcessObject[T](nodeDefinition: ObjectWithMethodDef,
                                     parameters: List[evaluatedparam.Parameter],
                                     branchParameters: List[BranchParameters],
                                     outputVariableNameOpt: Option[String],
                                     ctxOrBranches: Either[ValidationContext, BranchEndContexts],
                                     parameterDefinitionsToUse: Option[List[Parameter]] = None
                                    )
                                    (implicit nodeId: NodeId,
                                     metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    val ctx = ctxOrBranches.left.getOrElse(contextWithOnlyGlobalVariables)
    val branchContexts = ctxOrBranches.right.map { ctxs =>
      branchParameters.map(_.branchId).map(branchId => branchId -> ctxs.contextForId(branchId)).toMap
    }.right.getOrElse(Map.empty)

    val compiledObjectWithTypingInfo = objectParametersExpressionCompiler.compileObjectParameters(parameterDefinitionsToUse.getOrElse(nodeDefinition.parameters),
      parameters, branchParameters, ctx, branchContexts, eager = false).andThen { compiledParameters =>
      factory.createObject[T](nodeDefinition, outputVariableNameOpt, compiledParameters).map { obj =>
        val typingInfo = compiledParameters.flatMap {
          case (TypedParameter(name, TypedExpression(_, _, typingInfo)), _) =>
            List(name -> typingInfo)
          case (TypedParameter(paramName, TypedExpressionMap(valueByBranch)), _) =>
            valueByBranch.map {
              case (branch, TypedExpression(_, _, typingInfo)) =>
                val expressionId = NodeTypingInfo.branchParameterExpressionId(paramName, branch)
                expressionId -> typingInfo
            }
        }.toMap
        (typingInfo, obj)
      }
    }
    (compiledObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), compiledObjectWithTypingInfo.map(_._2))
  }

  private case class BranchEndContexts(contexts: Map[String, ValidationContext]) {

    def addPart(part: ProcessPart, result: CompilationResult[_]): BranchEndContexts = {
      val branchEnds = NodesCollector.collectNodesInAllParts(part).collect {
        case splittednode.EndingNode(BranchEndData(definition)) => definition.id -> result.typing.apply(definition.artificialNodeId)
      }.toMap
      copy(contexts = contexts ++ branchEnds.mapValues(_.inputValidationContext))
    }

    def contextForId(id: String)(implicit metaData: MetaData): ValidationContext = {
      contexts(id)
    }
  }

  object SourceNodeCompiler {

    def compileSourcePart(part: SourcePart, sourceData: SourceNodeData)
                         (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.part.SourcePart] = {
      val (typingInfo, initialCtx, compiledSource) = compileNode(sourceData)

      val validatedSource = sub.validate(part.node, initialCtx.valueOr(_ => contextWithOnlyGlobalVariables))
      val typesForParts = validatedSource.typing.mapValues(_.inputValidationContext)
      val nodeTypingInfo = Map(part.id -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo))

      CompilationResult.map4(
        validatedSource,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(initialCtx),
        CompilationResult(nodeTypingInfo, compiledSource)) { (_, nextParts, ctx, obj) =>
        compiledgraph.part.SourcePart(obj, splittednode.SourceNode(sourceData, part.node.next), ctx, nextParts, part.ends)
      }
    }

    private def compileNode(nodeData: SourceNodeData)(implicit metaData: MetaData, nodeId: NodeId): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, ValidationContext],
      ValidatedNel[ProcessCompilationError, Source[_]]) = nodeData match {
      case a@pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
        sourceFactories.get(ref.typ) match {
          case Some(definition) =>
            def defaultContextTransformation(compiled: Option[Any]) =
              contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, compiled.flatMap(a => returnType(definition, a)).getOrElse(Unknown))

            compileObjectWithTransformation[Source[_]](a, Left(contextWithOnlyGlobalVariables), Some(Interpreter.InputParamName), definition, defaultContextTransformation)
          case None =>
            val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
            //TODO: is this default behaviour ok?
            val defaultCtx = contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, Unknown)
            (Map.empty, defaultCtx, error)
        }
      case SubprocessInputDefinition(_, params, _) =>
        (Map.empty, Valid(contextWithOnlyGlobalVariables.copy(localVariables = params.map(p => p.name -> loadFromParameter(p)).toMap)), Valid(new Source[Any] {}))
    }

    //TODO: better classloader error handling
    private def loadFromParameter(subprocessParameter: SubprocessParameter)(implicit nodeId: NodeId) =
      subprocessParameter.typ.toRuntimeClass(classLoader).map(Typed(_)).getOrElse(throw new IllegalArgumentException(
        s"Failed to load subprocess parameter: ${subprocessParameter.typ.refClazzName} for ${nodeId.id}"))

  }

  object SinkNodeCompiler {

    def compileSinkPart(node: EndingNode[Sink], ctx: ValidationContext)(implicit metaData: MetaData, nodeId: NodeId): CompilationResult[part.SinkPart] = {
      val (typingInfo, compiledSink) = compile(node.data, ctx)
      val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo))
      CompilationResult.map2(sub.validate(node, ctx), CompilationResult(nodeTypingInfo, compiledSink))((_, obj) =>
        compiledgraph.part.SinkPart(obj, node, ctx)
      )
    }

    private def compile(sink: node.Sink, ctx: ValidationContext)
                       (implicit nodeId: NodeId,
                        metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, api.process.Sink]) = {
      val ref = sink.ref
      sinkFactories.get(ref.typ) match {
        case Some(definition) =>
          val (typeInfo, _, validSinkFactory) =
            compileObjectWithTransformation[api.process.Sink](sink, Left(ctx), None, definition._1, (_: Any) => Valid(ctx))
          (typeInfo, validSinkFactory)
        case None =>
          val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
          (Map.empty[String, ExpressionTypingInfo], error)
      }
    }
  }

  object CustomNodeCompiler {

    def compileEndingCustomNodePart(node: splittednode.EndingNode[CustomNode], data: CustomNodeData,
                                    ctx: ValidationContext)
                                   (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
      val (typingInfo, validatedNextCtx, compiledNode) = compileCustomNodeObject(data, Left(ctx), ending = true)
      val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo))

      CompilationResult.map2(
        CompilationResult(nodeTypingInfo, compiledNode),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, nextCtx) =>
        compiledgraph.part.CustomNodePart(nodeInvoker, node, nextCtx, List.empty, List(NormalEnd(node.id)))
      }.distinctErrors
    }

    def compileCustomNodePart(part: ProcessPart, node: splittednode.OneOutputNode[CustomNodeData], data: CustomNodeData,
                              ctx: Either[ValidationContext, BranchEndContexts])
                             (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
      val (typingInfo, validatedNextCtx, compiledNode) = compileCustomNodeObject(data, ctx, ending = false)

      val nextPartsValidation = sub.validate(node, validatedNextCtx.valueOr(_ => ctx.left.getOrElse(contextWithOnlyGlobalVariables)))
      val typesForParts = nextPartsValidation.typing.mapValues(_.inputValidationContext)
      val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx.left.getOrElse(contextWithOnlyGlobalVariables), typingInfo))

      CompilationResult.map4(
        CompilationResult(nodeTypingInfo, compiledNode),
        nextPartsValidation,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(validatedNextCtx)
      ) { (nodeInvoker, _, nextPartsCompiled, nextCtx) =>
        compiledgraph.part.CustomNodePart(nodeInvoker, node, nextCtx, nextPartsCompiled, part.ends)
      }.distinctErrors
    }

    private def compileCustomNodeObject(data: CustomNodeData, ctx: Either[ValidationContext, BranchEndContexts], ending: Boolean)
                                       (implicit metaData: MetaData, nodeId: NodeId):
    (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, ValidationContext], ValidatedNel[ProcessCompilationError, AnyRef]) = {
      val outputVar = data.outputVar

      val defaultCtx = ctx.fold(identity, _ => contextWithOnlyGlobalVariables)
      val defaultCtxToUse = data.outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

      customStreamTransformers.get(data.nodeType) match {
        case Some((_, additionalData)) if ending && !additionalData.canBeEnding =>
          val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(nodeId.id)))
          (Map.empty, defaultCtxToUse, error)
        case Some((nodeDefinition, additionalData)) =>
          val default = defaultContextAfter(additionalData, data, ending, ctx, nodeDefinition)
          compileObjectWithTransformation(data, ctx, outputVar, nodeDefinition, default)
        case None =>
          val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
          (Map.empty, defaultCtxToUse, error)
      }
    }

    private def defaultContextAfter(additionalData: CustomTransformerAdditionalData,
                                    node: CustomNodeData, ending: Boolean,
                                    branchCtx: Either[ValidationContext, BranchEndContexts],
                                    nodeDefinition: ObjectWithMethodDef)
                                   (implicit nodeId: NodeId, metaData: MetaData): Option[AnyRef] => ValidatedNel[ProcessCompilationError, ValidationContext] = maybeCNode => {
      val validationContext = branchCtx.left.getOrElse(contextWithOnlyGlobalVariables)
      val maybeClearedContext = if (additionalData.clearsContext) validationContext.clearVariables else validationContext

      def ctxWithVar(varName: String, typ: TypingResult) = maybeClearedContext.withVariable(varName, typ)
        //ble... NonEmptyList is invariant...
        .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

      maybeCNode match {
        case None => node.outputVar.map(ctxWithVar(_, Unknown)).getOrElse(Valid(maybeClearedContext))
        case Some(cNode) =>
          (node.outputVar, returnType(nodeDefinition, cNode)) match {
            case (Some(varName), Some(typ)) => ctxWithVar(varName, typ)
            case (None, None) => Valid(maybeClearedContext)
            case (Some(_), None) => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
            case (None, Some(_)) if ending => Valid(maybeClearedContext)
            case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
          }
      }
    }
  }

}

object ProcessCompiler {

  def apply(classLoader: ClassLoader,
            definitions: ProcessDefinition[ObjectWithMethodDef],
            dictRegistry: DictRegistry,
            expressionCompilerCreate: (ClassLoader, DictRegistry, ExpressionDefinition[ObjectWithMethodDef], ClassExtractionSettings) => ExpressionCompiler): ProcessCompiler = {
    val expressionCompiler = expressionCompilerCreate(classLoader, dictRegistry, definitions.expressionConfig, definitions.settings)
    val sub = new PartSubGraphCompiler(classLoader, expressionCompiler, definitions.expressionConfig, definitions.services)
    new ProcessCompiler(classLoader, sub, definitions, expressionCompiler)
  }

}

object ProcessValidator {

  def default(definitions: ProcessDefinition[ObjectWithMethodDef], dictRegistry: DictRegistry, loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    ProcessCompiler(loader, definitions, dictRegistry, ExpressionCompiler.withoutOptimization)
  }

}
