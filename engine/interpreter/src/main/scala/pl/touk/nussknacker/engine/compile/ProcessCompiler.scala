package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.{MaybeArtificial, ProcessCanonizer}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.part.NextWithParts
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{Sink => _, Source => _, _}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam}
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, NextNode, PartRef, SplittedNode}
import pl.touk.nussknacker.engine.util.Implicits._
import shapeless.syntax.typeable._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.control.NonFatal

class ProcessCompiler(protected val classLoader: ClassLoader,
                      protected val sub: PartSubGraphCompiler,
                      protected val definitions: ProcessDefinition[ObjectWithMethodDef]
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

  type ParameterProviderT = ObjectWithMethodDef

  protected def definitions: ProcessDefinition[ParameterProviderT]
  protected def sourceFactories = definitions.sourceFactories
  protected def sinkFactories = definitions.sinkFactories
  protected def exceptionHandlerFactory = definitions.exceptionHandlerFactory
  protected val customStreamTransformers = definitions.customStreamTransformers
  protected val expressionConfig = definitions.expressionConfig

  protected def sub: PartSubGraphCompiler

  private val syntax = ValidatedSyntax[ProcessCompilationError]
  import syntax._

  protected def classLoader: ClassLoader

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(classLoader, expressionConfig)

  //TODO: this should be refactored, now it's easy to forget about global vars in different places...
  private val globalVariableTypes = expressionConfig.globalVariables.mapValuesNow(_.returnType) + (Interpreter.MetaParamName -> Typed[MetaVariables])

  protected def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    compile(ProcessSplitter.split(process))
  }

  private def compile(splittedProcess: SplittedProcess): CompilationResult[CompiledProcessParts] = {
    implicit val metaData = splittedProcess.metaData
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

  private def returnType(nodeDefinition: ParameterProviderT, obj: AnyRef): Option[TypingResult] = {
    if (obj.isInstanceOf[ReturningType]) {
      Some(obj.asInstanceOf[ReturningType].returnType)
    } else if (nodeDefinition.hasNoReturn) {
      None
    } else {
      Some(nodeDefinition.returnType)
    }
  }

  private def contextAfterCustomNode(node: CustomNode, returnType: Option[TypingResult],
                                     validationContext: ValidationContext, clearsContext: Boolean)
                                    (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val maybeClearedContext = if (clearsContext) validationContext.copy(variables = globalVariableTypes) else validationContext

    (node.outputVar, returnType) match {
      case (Some(varName), Some(typ)) => maybeClearedContext.withVariable(varName, typ)
        //ble... NonEmptyList is invariant...
        .asInstanceOf[ValidatedNel[ProcessCompilationError,ValidationContext]]
      case (None, None) => Valid(maybeClearedContext)
      case (Some(_), None) => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
      case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
    }
  }

  private def compile(part: SubsequentPart, ctx: ValidationContext)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.SubsequentPart] = {
    implicit val nodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        CompilationResult.map2(sub.validate(node, ctx), CompilationResult(compile(node.data.ref)))((_, obj) =>
          compiledgraph.part.SinkPart(obj, node, ctx)
        )
      case CustomNodePart(node, nextParts, ends) =>
        val customNodeDefinition = getCustomNodeDefinition(node)
        val compiledNode = customNodeDefinition.andThen(n => compileCustomNodeInvoker(node.data, n._1, ctx))

        val nextCtx = A.map2(customNodeDefinition, compiledNode)((a, b) => (a,b)) andThen { case ((nodeDefinition, additionalData), cNode) =>
          contextAfterCustomNode(node.data, returnType(nodeDefinition, cNode), ctx, additionalData.clearsContext)
        }
        val nextPartsValidation = sub.validate(node, nextCtx.fold(_ => ctx, identity))

        CompilationResult.map4(
          f0 = CompilationResult(compiledNode),
          f1 = nextPartsValidation,
          f2 = compile(nextParts, nextPartsValidation.typing),
          f3 = CompilationResult(nextCtx)
        ) { (nodeInvoker, _, nextPartsCompiled, validatedNextCtx) =>
          compiledgraph.part.CustomNodePart(nodeInvoker, node, validatedNextCtx, nextPartsCompiled, ends)
        }.distinctErrors
    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.StartPart] = {
    implicit val nodeId = NodeId(source.id)

    source.node.data match {
      case sourceData:SourceNodeData =>
        val compiledSource = compile(sourceData)
        val initialCtx = computeInitialVariables(sourceData, compiledSource)
        val validatedSource = sub.validate(source.node, initialCtx)
        val typesForParts = validatedSource.typing

        CompilationResult.map3(validatedSource, compile(source.nextParts, typesForParts), CompilationResult(compiledSource)) { (_, nextParts, obj) =>
          compiledgraph.part.SourcePart(obj,
            splittednode.SourceNode(sourceData, source.node.next), initialCtx, nextParts, source.ends)
        }


      case join:Join =>

        val ref = join.ref.typ
        val customData = fromOption[ProcessCompilationError, (ParameterProviderT, CustomTransformerAdditionalData)](customStreamTransformers.get(ref), MissingCustomNodeExecutor(ref))
                .toValidatedNel

        val initialContext = ValidationContext(Map(join.outputVar.get -> Unknown) ++ globalVariableTypes)

        val compiledJoin: ValidatedNel[ProcessCompilationError, AnyRef] = customData.andThen(d => compileCustomNodeInvoker(join, d._1, initialContext))
        //TODO JOIN: here we need to add handling ValidationContext, and input variables
        val validatedSource = sub.validate(source.node, initialContext)
        val typesForParts = validatedSource.typing

        CompilationResult.map3(validatedSource, compile(source.nextParts, typesForParts), CompilationResult(compiledJoin)) { (_, nextParts, obj) =>
          compiledgraph.part.JoinPart(obj, splittednode.SourceNode(join, source.node.next), initialContext, initialContext, nextParts, source.ends)
        }
    }

  }

  private def computeInitialVariables(nodeData: SourceNodeData, compiled: ValidatedNel[ProcessCompilationError, Source[_]])(implicit metaData: MetaData, nodeId: NodeId) : ValidationContext = ValidationContext(nodeData match {
    case pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
      val resultType = compiled.toOption.flatMap[Source[_]](Option(_))
        .flatMap(_.cast[ReturningType]).map(_.returnType)
        .orElse(sourceFactories.get(ref.typ).map(_.returnType)).getOrElse(Unknown)
      Map(
        Interpreter.InputParamName -> resultType
      ) ++ globalVariableTypes
      //TODO: here is nasty edge case - what if subprocess parameter is named like global variable?
    case SubprocessInputDefinition(_, params, _) => params.map(p => p.name -> loadFromParameter(p)).toMap ++ globalVariableTypes
  })

  //TODO: better classloader error handling
  private def loadFromParameter(subprocessParameter: SubprocessParameter)(implicit nodeId: NodeId) =
    subprocessParameter.typ.toTyped(classLoader).getOrElse(throw new IllegalArgumentException(
      s"Failed to load subprocess parameter: ${subprocessParameter.typ.refClazzName} for ${nodeId.id}"))

  private def compile(ref: ExceptionHandlerRef)
                      (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, EspExceptionHandler] = {
    implicit val nodeId = NodeId(ProcessCompilationError.ProcessNodeId)
    if (metaData.isSubprocess) {
      //FIXME: what should be here?
      Valid(new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      })
    } else {
      compileProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters)
    }
  }

  private def compile(nodeData: SourceNodeData)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Source[Any]] = nodeData match {
    case pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
      val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
        validSourceFactory.andThen(sourceFactory => compileProcessObject[Source[Any]](sourceFactory, ref.parameters))
    case SubprocessInputDefinition(_, _, _) => Valid(new Source[Any]{}) //FIXME: How should this be handled?
  }

  private def compile(ref: SinkRef)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Sink] = {
    val validSinkFactory = sinkFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSinkFactory(ref.typ))).toValidatedNel
    validSinkFactory.andThen(sinkFactory => compileProcessObject[Sink](sinkFactory, ref.parameters))
  }

  private def compileProcessObject[T](parameterProviderT: ParameterProviderT,
                                      parameters: List[evaluatedparam.Parameter], ctx: ValidationContext = ValidationContext(globalVariableTypes))
                                     (implicit nodeId: NodeId,
                                      metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {

    validateParameters(parameterProviderT, parameters, ctx).andThen { compiledParameters =>
      try {
        Valid(factory.create[T](parameterProviderT, compiledParameters))
      } catch {
        case NonFatal(e) =>
          //TODO: better message?
          Invalid(NonEmptyList.of(CannotCreateObjectError(e.getMessage, nodeId.id)))
      }
    }
  }

  private def getCustomNodeDefinition(node: SplittedNode[graph.node.CustomNode])(implicit nodeId: NodeId, metaData: MetaData) = {
    val ref = node.data.nodeType
    fromOption[ProcessCompilationError, (ParameterProviderT, CustomTransformerAdditionalData)](customStreamTransformers.get(ref), MissingCustomNodeExecutor(ref))
          .toValidatedNel
  }

  private def compileCustomNodeInvoker(node: WithParameters, nodeDefinition: ParameterProviderT, ctx: ValidationContext)
                                      (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, AnyRef] = {
    compileProcessObject[AnyRef](nodeDefinition, node.parameters, ctx)
  }

  protected def factory: ProcessObjectFactory

  private def compile(parts: List[SubsequentPart], ctx: Map[String, ValidationContext])
                     (implicit metaData: MetaData): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts.map(p =>
      ctx.get(p.id).map(compile(p, _)).getOrElse(CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id)))))
    ).sequence
  }

  private def validateParameters(parameterProvider: ParameterProviderT, parameters: List[evaluatedparam.Parameter], ctx: ValidationContext)
                                (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {

    expressionCompiler.compileObjectParameters(parameterProvider.parameters, parameters, Some(ctx)).andThen { compiledParams =>

      val definedParamNames = parameterProvider.parameters.map(_.name).toSet
      val usedParamNamesSet = parameters.map(_.name).toSet
      val missingParams = definedParamNames.diff(usedParamNamesSet)
      val redundantParams = usedParamNamesSet.diff(definedParamNames)
      val notMissing = if (missingParams.nonEmpty) invalid(MissingParameters(missingParams)) else valid(Unit)
      val notRedundant = if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)) else valid(Unit)
      A.map2(
        notMissing.toValidatedNel,
        notRedundant.toValidatedNel
      ) { (_, _) => compiledParams }.leftMap(_.map(identity[ProcessCompilationError]))
    }
  }

  private def validate(n: Next, ctx: ValidationContext): CompilationResult[Unit] = n match {
    case NextNode(node) => sub.validate(node, ctx)
    //TODO: what should be here??
    case PartRef(id) => CompilationResult(Map(id -> ctx), Valid(()))
  }


}

object ProcessValidator {

  def default(definitions: ProcessDefinition[ObjectWithMethodDef], loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    val expressionCompiler = ExpressionCompiler.withoutOptimization(loader, definitions.expressionConfig)

    val sub = new PartSubGraphCompiler(
      loader, expressionCompiler, definitions.expressionConfig, definitions.services)

    new ProcessCompiler(loader, sub, definitions)
  }

}