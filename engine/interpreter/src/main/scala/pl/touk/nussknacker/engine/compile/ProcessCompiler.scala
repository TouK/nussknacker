package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import cats.syntax.apply._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{AbstractContextTransformation, AbstractContextTransformationDef, ContextTransformationDef, JoinContextTransformationDef, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MissingOutputVariableException
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{Sink => _, Source => _, _}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam}
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.variables.MetaVariables
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
  private val expressionEvaluator = {
    val globalVars = expressionConfig.globalVariables.mapValuesNow(_.obj)
    ExpressionEvaluator.withoutLazyVals(globalVars, List())
  }

  override def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }

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

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext = {
    val globalTypes = expressionConfig.globalVariables.mapValuesNow(_.returnType)
    val typesWithMeta = MetaVariables.withType(globalTypes)

    ValidationContext(Map.empty, typesWithMeta)
  }

  protected def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      compile(ProcessSplitter.split(process))
    }
  }

  private def compile(splittedProcess: SplittedProcess): CompilationResult[CompiledProcessParts] = {
    implicit val metaData: MetaData = splittedProcess.metaData
    CompilationResult.map3(
      CompilationResult(findDuplicates(splittedProcess.sources).toValidatedNel),
      CompilationResult(compile(splittedProcess.exceptionHandlerRef)),
      splittedProcess.sources.map(compile).sequence
    ) { (_, exceptionHandler, sources) =>
      CompiledProcessParts(splittedProcess.metaData, exceptionHandler, sources)
    }
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
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, EspExceptionHandler] = {
    implicit val nodeId: NodeId = NodeId(ProcessCompilationError.ProcessNodeId)
    if (metaData.isSubprocess) {
      //FIXME: what should be here?
      Valid(new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      })
    } else {
      compileProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters, List.empty, outputVariableNameOpt = None, contextWithOnlyGlobalVariables)
    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    implicit val nodeId: NodeId = NodeId(source.id)

    source match {
      case SourcePart(node@splittednode.SourceNode(sourceData:SourceNodeData, _), _, _) =>
        SourceNodeCompiler.compileSourcePart(source, node, sourceData)
      case SourcePart(srcNode@splittednode.SourceNode(data: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        //TODO JOIN: here we need to add handling ValidationContext from branches
        val ctx = contextWithOnlyGlobalVariables
          // This input variable is because usually join will be just after source, and there is a chance, that
          // it will be in context. Thanks to this user can use this variable in branch parameters.
          .withVariable(Interpreter.InputParamName, Unknown).toOption.get

        CustomNodeCompiler.compileCustomNodePart(source, node, data, ctx)
    }

  }

  object SourceNodeCompiler {

    def compileSourcePart(part: SourcePart, node: splittednode.SourceNode[StartingNodeData],
                          sourceData: SourceNodeData)
                         (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.part.SourcePart] = {
      val compiledSource = compile(sourceData)
      val initialCtx = computeInitialVariables(sourceData, compiledSource)

      val validatedSource = sub.validate(node, initialCtx)
      val typesForParts = validatedSource.typing

      CompilationResult.map3(
        validatedSource,
        compileParts(part.nextParts, typesForParts),
        CompilationResult(Map(node.id -> contextWithOnlyGlobalVariables), compiledSource)) { (_, nextParts, obj) =>
        compiledgraph.part.SourcePart(obj,
          splittednode.SourceNode(sourceData, node.next), initialCtx, nextParts, part.ends)
      }
    }

    private def compile(nodeData: SourceNodeData)
                       (implicit nodeId: NodeId,
                        metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Source[Any]] = nodeData match {
      case pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
        val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
        validSourceFactory.andThen(sourceFactory => compileProcessObject[Source[Any]](sourceFactory, ref.parameters, List.empty,
          outputVariableNameOpt = None, contextWithOnlyGlobalVariables))
      case SubprocessInputDefinition(_, _, _) => Valid(new Source[Any] {}) //FIXME: How should this be handled?
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
        CompilationResult.map2(sub.validate(node, ctx), CompilationResult(compile(node.data.ref, ctx)))((_, obj) =>
          compiledgraph.part.SinkPart(obj, node, ctx)
        )
      case CustomNodePart(node@splittednode.OneOutputSubsequentNode(data, _), _, _) =>
        CustomNodeCompiler.compileCustomNodePart(part, node, data, ctx)
    }
  }

  private def compile(ref: SinkRef, ctx: ValidationContext)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Sink] = {
    val validSinkFactory = sinkFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSinkFactory(ref.typ))).toValidatedNel
    validSinkFactory.andThen(sinkFactory => compileProcessObject[Sink](sinkFactory._1, ref.parameters, List.empty, outputVariableNameOpt = None, ctx = ctx))
  }

  object CustomNodeCompiler {

    def compileCustomNodePart(part: ProcessPart, node: splittednode.OneOutputNode[CustomNodeData], data: CustomNodeData,
                              ctx: ValidationContext)
                             (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
      val (compiledNode, nextCtx) = compileCustomNodeObject(data, ctx)
      val nextPartsValidation = sub.validate(node, nextCtx.valueOr(_ => ctx))
      val typesForParts = nextPartsValidation.typing

      CompilationResult.map4(
        f0 = CompilationResult(compiledNode),
        f1 = nextPartsValidation,
        f2 = compileParts(part.nextParts, typesForParts),
        f3 = CompilationResult(nextCtx)
      ) { (nodeInvoker, _, nextPartsCompiled, validatedNextCtx) =>
        compiledgraph.part.CustomNodePart(nodeInvoker, node, validatedNextCtx, nextPartsCompiled, part.ends)
      }.distinctErrors
    }

    private def compileCustomNodeObject(data: CustomNodeData, ctx: ValidationContext)
                                       (implicit metaData: MetaData, nodeId: NodeId) = {
      val customNodeDefinition = fromOption(
        customStreamTransformers.get(data.nodeType), MissingCustomNodeExecutor(data.nodeType)
      ).toValidatedNel
      val compiledNode = customNodeDefinition.andThen {
        case (nodeDefinition, _) =>
          compileProcessObject[AnyRef](nodeDefinition, data.parameters,
            data.cast[Join].map(_.branchParameters).getOrElse(List.empty), data.outputVar, ctx)
      }

      val nextCtx = (customNodeDefinition, compiledNode).mapN(Tuple2.apply) andThen {
        case ((nodeDefinition, additionalData), cNode) =>
          val contextTransformationDefOpt = cNode.cast[AbstractContextTransformation].map(_.definition)
          contextAfterCustomNode(data, nodeDefinition, cNode, ctx, additionalData.clearsContext, contextTransformationDefOpt)
      }
      (compiledNode, nextCtx)
    }

    private def contextAfterCustomNode(node: CustomNodeData, nodeDefinition: ObjectWithMethodDef, cNode: AnyRef,
                                       validationContext: ValidationContext, clearsContext: Boolean,
                                       contextTransformationDefOpt: Option[AbstractContextTransformationDef])
                                      (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
      contextTransformationDefOpt match {
        case Some(transformation: ContextTransformationDef) =>
          transformation.transform(validationContext)
        case Some(transformation: JoinContextTransformationDef) =>
          // TODO JOIN: better error
          val joinNode = node.cast[Join].getOrElse(throw new IllegalArgumentException(s"Should be used join element in node ${nodeId.id}"))
          // TODO JOIN: use correct contexts for branches
          val contexts = joinNode.branchParameters.groupBy(_.branchId).mapValuesNow(_ => validationContext)
          transformation.transform(contexts)
        case None =>
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
                                      branchParameters: List[evaluatedparam.BranchParameters],
                                      outputVariableNameOpt: Option[String],
                                      ctx: ValidationContext)
                                     (implicit nodeId: NodeId,
                                      metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    objectParametersExpressionCompiler.compileObjectParameters(nodeDefinition.parameters,
      parameters,
      branchParameters, ctx.clearVariables, ctx).andThen { compiledParameters =>
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
  }

  protected def factory: ProcessObjectFactory

}


object ProcessCompiler {

  def apply(classLoader: ClassLoader,
            definitions: ProcessDefinition[ObjectWithMethodDef],
            expressionCompilerCreate: (ClassLoader, ExpressionDefinition[ObjectWithMethodDef]) => ExpressionCompiler): ProcessCompiler = {
    val expressionCompiler = expressionCompilerCreate(classLoader, definitions.expressionConfig)
    val sub = new PartSubGraphCompiler(classLoader, expressionCompiler, definitions.expressionConfig, definitions.services)
    new ProcessCompiler(classLoader, sub, definitions, expressionCompiler)
  }

}

object ProcessValidator {

  def default(definitions: ProcessDefinition[ObjectWithMethodDef], loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    ProcessCompiler(loader, definitions, ExpressionCompiler.withoutOptimization)
  }

}