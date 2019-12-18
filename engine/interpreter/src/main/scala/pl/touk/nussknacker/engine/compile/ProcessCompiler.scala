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
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.compiledgraph.part.PotentiallyStartPart
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MissingOutputVariableException
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{Sink => _, Source => _, _}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam}
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
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
    ExpressionEvaluator.withoutLazyVals(GlobalVariablesPreparer(expressionConfig), List.empty)

  override def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }

  override protected def factory: ProcessObjectFactory = new ProcessObjectFactory(expressionEvaluator)

  override def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ProcessCompiler =
    new ProcessCompiler(classLoader, sub.withExpressionParsers(modify), definitions, objectParametersExpressionCompiler.withExpressionParsers(modify))

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

  protected def compile(process : EspProcess): CompilationResult[_]

}

protected trait ProcessCompilerBase {

  protected def definitions: ProcessDefinition[ObjectWithMethodDef]
  protected def sourceFactories: Map[String, ObjectWithMethodDef] = definitions.sourceFactories
  protected def sinkFactories: Map[String, (ObjectWithMethodDef, ProcessDefinitionExtractor.SinkAdditionalData)] = definitions.sinkFactories
  protected def exceptionHandlerFactory: ObjectWithMethodDef = definitions.exceptionHandlerFactory
  protected val customStreamTransformers: Map[String, (ObjectWithMethodDef, ProcessDefinitionExtractor.CustomTransformerAdditionalData)] = definitions.customStreamTransformers
  protected val expressionConfig: ProcessDefinitionExtractor.ExpressionDefinition[ObjectWithMethodDef] = definitions.expressionConfig

  protected def sub: PartSubGraphCompiler

  private val syntax = ValidatedSyntax[ProcessCompilationError]
  import syntax._
  implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])

  protected def classLoader: ClassLoader

  protected def objectParametersExpressionCompiler: ExpressionCompiler

  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext = {
    val globalTypes = globalVariablesPreparer.prepareGlobalVariables(metaData).mapValuesNow(_.typ)

    ValidationContext(Map.empty, globalTypes)
  }

  protected def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      compile(ProcessSplitter.split(process))
    }
  }

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
    val zeroAcc = (CompilationResult(Valid(List[PotentiallyStartPart]())), BranchEndContexts(Map(), None))
    //we use fold here (and not map/sequence), because we can compile part which starts from Join only when we
    //know compilation results (stored in BranchEndContexts) of all branches that end in this join
    val (result, _) = PartSort.sort(sources.toList).foldLeft(zeroAcc) { case ((resultSoFar, branchContexts), nextSourcePart) =>
        val compiledPart = compile(nextSourcePart, branchContexts)
        //we don't use andThen on CompilationResult, since we don't want to stop if there are errors in part
        val nextResult = CompilationResult.map2(resultSoFar, compiledPart)(_ :+ _)
        (nextResult, branchContexts.addPart(nextSourcePart.node, compiledPart))
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
      compileProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters, List.empty, outputVariableNameOpt = None, Left(contextWithOnlyGlobalVariables))
    }
  }

  private def compile(source: SourcePart, branchEndContexts: BranchEndContexts)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    implicit val nodeId: NodeId = NodeId(source.id)

    source match {
      case SourcePart(node@splittednode.SourceNode(sourceData:SourceNodeData, _), _, _) =>
        SourceNodeCompiler.compileSourcePart(source, node, sourceData)
      case SourcePart(srcNode@splittednode.SourceNode(data: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        CustomNodeCompiler.compileCustomNodePart(source, node, data, Right(branchEndContexts))
    }

  }

  object SourceNodeCompiler {

    def compileSourcePart(part: SourcePart, node: splittednode.SourceNode[StartingNodeData],
                          sourceData: SourceNodeData)
                         (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.part.SourcePart] = {
      val (typingInfo, compiledSource) = compile(sourceData)
      val initialCtx = computeInitialVariables(sourceData, compiledSource)

      val validatedSource = sub.validate(node, initialCtx)
      val typesForParts = validatedSource.typing.mapValues(_.inputValidationContext)
      val nodeTypingInfo = Map(node.id -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo))

      CompilationResult.map3(
        validatedSource,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(nodeTypingInfo, compiledSource)) { (_, nextParts, obj) =>
        compiledgraph.part.SourcePart(obj, splittednode.SourceNode(sourceData, node.next), initialCtx, nextParts, part.ends)
      }
    }

    private def compile(nodeData: SourceNodeData)
                       (implicit nodeId: NodeId,
                        metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, api.process.Source[Any]]) = nodeData match {
      case pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
        val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
        val validObjectWithTypingInfo = validSourceFactory.andThen { sourceFactory =>
          val (typingInfo, validProcessObject) = compileProcessObject[Source[Any]](sourceFactory, ref.parameters, List.empty, outputVariableNameOpt = None,
            Left(contextWithOnlyGlobalVariables))
          validProcessObject.map((typingInfo, _))
        }
        (validObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), validObjectWithTypingInfo.map(_._2))
      case SubprocessInputDefinition(_, _, _) => (Map.empty, Valid(new Source[Any] {})) //FIXME: How should this be handled?
    }

    private def computeInitialVariables(nodeData: SourceNodeData, compiled: ValidatedNel[ProcessCompilationError, Source[_]])(implicit metaData: MetaData, nodeId: NodeId): ValidationContext = {
      val variables = nodeData match {
        case pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
          val resultType = compiled.toOption.flatMap[Source[_]](Option(_))
            .flatMap(_.cast[ReturningType]).map(_.returnType)
            .orElse(sourceFactories.get(ref.typ).map(_.returnType)).getOrElse(Unknown)
          Map(
            Interpreter.InputParamName -> resultType
          )
        //TODO: here is nasty edge case - what if subprocess parameter is named like global variable?
        case SubprocessInputDefinition(_, params, _) => params.map(p => p.name -> loadFromParameter(p)).toMap
      }
      contextWithOnlyGlobalVariables.copy(localVariables = variables)
    }

    //TODO: better classloader error handling
    private def loadFromParameter(subprocessParameter: SubprocessParameter)(implicit nodeId: NodeId) =
      subprocessParameter.typ.toRuntimeClass(classLoader).map(Typed(_)).getOrElse(throw new IllegalArgumentException(
        s"Failed to load subprocess parameter: ${subprocessParameter.typ.refClazzName} for ${nodeId.id}"))

  }

  private def compileParts(parts: List[SubsequentPart], ctx: Map[String, ValidationContext])
                          (implicit metaData: MetaData): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts.map(p =>
      ctx.get(p.id).map(compile(p, _)).getOrElse(CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id)))))
    ).sequence
  }

  private def compile(part: SubsequentPart, ctx: ValidationContext)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.SubsequentPart] = {
    implicit val nodeId: NodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        val (typingInfo, compiledSink) = compile(node.data.ref, ctx)
        val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo))
        CompilationResult.map2(sub.validate(node, ctx), CompilationResult(nodeTypingInfo, compiledSink))((_, obj) =>
          compiledgraph.part.SinkPart(obj, node, ctx)
        )
      case CustomNodePart(node@splittednode.OneOutputSubsequentNode(data, _), _, _) =>
        CustomNodeCompiler.compileCustomNodePart(part, node, data, Left(ctx))
    }
  }

  private def compile(ref: SinkRef, ctx: ValidationContext)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, api.process.Sink]) = {
    val validSinkFactory = sinkFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSinkFactory(ref.typ))).toValidatedNel
    val validObjectWithTypingInfo = validSinkFactory.andThen { sinkFactory =>
      val (typingInfo, validProcessObject) = compileProcessObject[Sink](sinkFactory._1, ref.parameters, List.empty, outputVariableNameOpt = None, Left(ctx))
      validProcessObject.map((typingInfo, _))
    }
    (validObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), validObjectWithTypingInfo.map(_._2))
  }

  object CustomNodeCompiler {

    def compileCustomNodePart(part: ProcessPart, node: splittednode.OneOutputNode[CustomNodeData], data: CustomNodeData,
                              ctx: Either[ValidationContext, BranchEndContexts])
                             (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
      val (typingInfo, validatedNextCtx, compiledNode) = compileCustomNodeObject(data, ctx)

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

    private def compileCustomNodeObject(data: CustomNodeData, ctx: Either[ValidationContext, BranchEndContexts])
                                       (implicit metaData: MetaData, nodeId: NodeId):
    (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, ValidationContext], ValidatedNel[ProcessCompilationError, AnyRef]) = {
      val customNodeDefinition = fromOption(
        customStreamTransformers.get(data.nodeType), MissingCustomNodeExecutor(data.nodeType)
      ).toValidatedNel
      val validObjectWithTypingInfo = customNodeDefinition.andThen {
        case (nodeDefinition, _) =>
          val (typingInfo, validProcessObject) = compileProcessObject[AnyRef](nodeDefinition, data.parameters,
            data.cast[Join].map(_.branchParameters).getOrElse(List.empty),
            data.outputVar, ctx)
          validProcessObject.map((typingInfo, _))
      }

      val nextCtx = (customNodeDefinition, validObjectWithTypingInfo).mapN(Tuple2.apply).andThen {
        case ((nodeDefinition, additionalData), (_, cNode)) =>
          val contextTransformationDefOpt = cNode.cast[AbstractContextTransformation].map(_.definition)
          contextAfterCustomNode(data, nodeDefinition, cNode, ctx, additionalData.clearsContext, contextTransformationDefOpt)
      }

      (validObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), nextCtx, validObjectWithTypingInfo.map(_._2))
    }

    private def contextAfterCustomNode(node: CustomNodeData, nodeDefinition: ObjectWithMethodDef, cNode: AnyRef,
                                       validationContexts: Either[ValidationContext, BranchEndContexts], clearsContext: Boolean,
                                       contextTransformationDefOpt: Option[AbstractContextTransformationDef])
                                      (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
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
        case (None, branchCtx) =>
          val validationContext = branchCtx.left.getOrElse(contextWithOnlyGlobalVariables)
          val maybeClearedContext = if (clearsContext) validationContext.clearVariables else validationContext

          (node.outputVar, returnType(nodeDefinition, cNode)) match {
            case (Some(varName), Some(typ)) => maybeClearedContext.withVariable(varName, typ)
              //ble... NonEmptyList is invariant...
              .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]
            case (None, None) => Valid(maybeClearedContext)
            case (Some(_), None) => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
            case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
          }
      }
    }

    private def returnType(nodeDefinition: ObjectWithMethodDef, obj: AnyRef): Option[TypingResult] = {
      if (obj.isInstanceOf[ReturningType]) {
        Some(obj.asInstanceOf[ReturningType].returnType)
      } else if (nodeDefinition.hasNoReturn) {
        None
      } else {
        Some(nodeDefinition.returnType)
      }
    }

  }

  private def compileProcessObject[T](nodeDefinition: ObjectWithMethodDef,
                                      parameters: List[evaluatedparam.Parameter],
                                      branchParameters: List[BranchParameters],
                                      outputVariableNameOpt: Option[String],
                                      ctxOrBranches: Either[ValidationContext, BranchEndContexts])
                                     (implicit nodeId: NodeId,
                                      metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    val ctx = ctxOrBranches.left.getOrElse(contextWithOnlyGlobalVariables)
    val branchContexts = ctxOrBranches.right.map { ctxs =>
      branchParameters.map(_.branchId).map(branchId => branchId -> ctxs.contextForId(branchId)).toMap
    }.right.getOrElse(Map.empty)

    val compiledObjectWithTypingInfo = objectParametersExpressionCompiler.compileObjectParameters(nodeDefinition.parameters,
      parameters, branchParameters, ctx, branchContexts, eager = false).andThen { compiledParameters =>
        createObject[T](nodeDefinition, outputVariableNameOpt, compiledParameters).map { obj =>
          val typingInfo = compiledParameters.flatMap {
            case TypedParameter(name, TypedExpression(_, _, typingInfo)) =>
              List(name -> typingInfo)
            case TypedParameter(paramName, TypedExpressionMap(valueByBranch)) =>
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


  private def createObject[T](nodeDefinition: ObjectWithMethodDef, outputVariableNameOpt: Option[String], compiledParameters: List[TypedParameter])
                             (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    try {
      Valid(factory.create[T](nodeDefinition, compiledParameters, outputVariableNameOpt))
    } catch {
      // TODO: using Validated in nested invocations
      case _: MissingOutputVariableException =>
        Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"), nodeId.id)))
      case NonFatal(e) =>
        //TODO: better message?
        Invalid(NonEmptyList.of(CannotCreateObjectError(e.getMessage, nodeId.id)))
    }
  }

  private case class BranchEndContexts(contexts: Map[String, Map[String, TypingResult]], parentContext: Option[ValidationContext]) {
    def addPart(node: SplittedNode[_<:NodeData], result: CompilationResult[_]): BranchEndContexts = {
      val branchEnds = SplittedNodesCollector.collectNodes(node).collect {
        case splittednode.EndingNode(BranchEndData(definition)) => definition.id -> result.variablesInNodes(definition.artificialNodeId)
      }.toMap
      copy(contexts = contexts ++ branchEnds)
    }

    def contextForId(id: String)(implicit metaData: MetaData): ValidationContext = {
      ValidationContext(contexts(id), contextWithOnlyGlobalVariables.globalVariables, parentContext)
    }
  }

  protected def factory: ProcessObjectFactory

}


object ProcessCompiler {

  def apply(classLoader: ClassLoader,
            definitions: ProcessDefinition[ObjectWithMethodDef],
            dictRegistry: DictRegistry,
            expressionCompilerCreate: (ClassLoader, DictRegistry, ExpressionDefinition[ObjectWithMethodDef]) => ExpressionCompiler): ProcessCompiler = {
    val expressionCompiler = expressionCompilerCreate(classLoader, dictRegistry, definitions.expressionConfig)
    val sub = new PartSubGraphCompiler(classLoader, expressionCompiler, definitions.expressionConfig, definitions.services)
    new ProcessCompiler(classLoader, sub, definitions, expressionCompiler)
  }

}

object ProcessValidator {

  def default(definitions: ProcessDefinition[ObjectWithMethodDef], dictRegistry: DictRegistry, loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    ProcessCompiler(loader, definitions, dictRegistry, ExpressionCompiler.withoutOptimization)
  }

}