package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{JoinGenericNodeTransformation, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, ServiceReturningType}
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo, NodeValidationExceptionHandler, ProcessObjectFactory}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{FinalStateValue, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.definition.{ProcessDefinitionExtractor, ServiceInvoker}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.{evaluatedparam, node}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{Interpreter, api, compiledgraph}
import shapeless.Typeable
import shapeless.syntax.typeable._
import cats.instances.list._
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.compile.NodeTypingInfo.DefaultExpressionId
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.{ExpressionCompilation, NodeCompilationResult}
import pl.touk.nussknacker.engine.definition.parameter.StandardParameterEnrichment

import scala.util.{Failure, Success, Try}

object NodeCompiler {

  case class NodeCompilationResult[T](expressionTypingInfo: Map[String, ExpressionTypingInfo],
                                      parameters: Option[List[Parameter]],
                                      validationContext: ValidatedNel[ProcessCompilationError, ValidationContext],
                                      compiledObject: ValidatedNel[ProcessCompilationError, T],
                                      expressionType: Option[TypingResult] = None) {
    def errors: List[ProcessCompilationError] = (validationContext.swap.toList ++ compiledObject.swap.toList).flatMap(_.toList)
  }

  private case class ExpressionCompilation[R](fieldName: String, 
                                              typedExpression: Option[TypedExpression],
                                              validated: ValidatedNel[ProcessCompilationError, R]) {

    val typingResult: TypingResult =
      typedExpression.map(_.returnType).getOrElse(Unknown)

    val expressionTypingInfo: Map[String, ExpressionTypingInfo] =
      typedExpression.map(te => (fieldName, te.typingInfo)).toMap
  }
}

class NodeCompiler(definitions: ProcessDefinition[ObjectWithMethodDef],
                   objectParametersExpressionCompiler: ExpressionCompiler,
                   classLoader: ClassLoader) {

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): NodeCompiler = {
    new NodeCompiler(definitions, objectParametersExpressionCompiler.withExpressionParsers(modify), classLoader)
  }

  type GenericValidationContext = Either[ValidationContext, Map[String, ValidationContext]]

  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  private val expressionConfig: ProcessDefinitionExtractor.ExpressionDefinition[ObjectWithMethodDef] = definitions.expressionConfig

  //FIXME: should it be here?
  private val expressionEvaluator =
    ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(expressionConfig))
  private val factory: ProcessObjectFactory = new ProcessObjectFactory(expressionEvaluator)

  def compileSource(nodeData: SourceNodeData)(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[Source[_]] = nodeData match {
    case a@pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
      definitions.sourceFactories.get(ref.typ) match {
        case Some(definition) =>
          def defaultContextTransformation(compiled: Option[Any]) =
            contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, compiled.flatMap(a => returnType(definition, a)).getOrElse(Unknown), paramName = None)

          compileObjectWithTransformation[Source[_]](a, Left(contextWithOnlyGlobalVariables), Some(Interpreter.InputParamName), definition, defaultContextTransformation)
        case None =>
          val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
          //TODO: is this default behaviour ok?
          val defaultCtx = contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, Unknown, paramName = None)
          NodeCompilationResult(Map.empty, None, defaultCtx, error)
      }
    case SubprocessInputDefinition(_, params, _) =>
      NodeCompilationResult(Map.empty, None, Valid(contextWithOnlyGlobalVariables.copy(localVariables = params.map(p => p.name -> loadFromParameter(p)).toMap)), Valid(new Source[Any] {}))
  }

  def compileCustomNodeObject(data: CustomNodeData, ctx: GenericValidationContext, ending: Boolean)
                             (implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[AnyRef] = {

    val outputVar = data.outputVar.map(OutputVar.customNode)
    val defaultCtx = ctx.fold(identity, _ => contextWithOnlyGlobalVariables)
    val defaultCtxToUse = outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.customStreamTransformers.get(data.nodeType) match {
      case Some((_, additionalData)) if ending && !additionalData.canBeEnding =>
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(nodeId.id)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
      case Some((nodeDefinition, additionalData)) =>
        val default = defaultContextAfter(additionalData, data, ending, ctx, nodeDefinition)
        compileObjectWithTransformation(data, ctx, outputVar.map(_.outputName), nodeDefinition, default)
      case None =>
        val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
    }
  }

  def compileSink(sink: node.Sink, ctx: ValidationContext)
                 (implicit nodeId: NodeId,
                  metaData: MetaData): NodeCompilationResult[api.process.Sink] = {
    val ref = sink.ref
    definitions.sinkFactories.get(ref.typ) match {
      case Some(definition) =>
        compileObjectWithTransformation[api.process.Sink](sink, Left(ctx), None, definition._1, (_: Any) => Valid(ctx))
      case None =>
        val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(ctx), error)
    }
  }

  def compileSubprocessInput(subprocessInput: SubprocessInput, ctx: ValidationContext)
                   (implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.evaluatedparam.Parameter]] = {

    val ref = subprocessInput.ref
    val validParamDefs = ref.parameters.map(p => getSubprocessParamDefinition(subprocessInput, p.name)).sequence
    val paramNamesWithType: List[(String, TypingResult)] = validParamDefs.map { ps =>
      ps.map(p => (p.name, p.typ))
    }.getOrElse(ref.parameters.map(p => (p.name, Unknown)))

    val childCtx = ctx.pushNewContext()
    val newCtx = paramNamesWithType.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx)) {
      case (acc, (paramName, typ)) => acc.andThen(_.withVariable(OutputVar.variable(paramName), typ))
    }
    val validParams = validParamDefs.andThen { paramDefs =>
      objectParametersExpressionCompiler.compileEagerObjectParameters(paramDefs, ref.parameters, ctx)
    }
    val expressionTypingInfo = validParams.map(_.map(p => p.name -> p.typingInfo).toMap).valueOr(_ => Map.empty[String, ExpressionTypingInfo])
    NodeCompilationResult(expressionTypingInfo, None, newCtx, validParams)
  }

  def compileFields(fields: List[graph.variable.Field],
                    ctx: ValidationContext,
                    outputVar: Option[OutputVar])
                   (implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {
    val compilationResult: ValidatedNel[ProcessCompilationError, List[ExpressionCompilation[compiledgraph.variable.Field]]] = fields.map { field =>
      objectParametersExpressionCompiler
        .compile(field.expression, Some(field.name), ctx, Unknown)
        .map(typedExpression => ExpressionCompilation(field.name, Some(typedExpression), Valid(compiledgraph.variable.Field(field.name, typedExpression.expression))))
    }.sequence

    val typedObject = compilationResult.map { fieldsComp =>
      TypedObjectTypingResult(fieldsComp.map(f => (f.fieldName, f.typingResult)).toMap)
    }.valueOr(_ => Unknown)

    val fieldsTypingInfo = compilationResult.map { compilations =>
      compilations.flatMap(_.expressionTypingInfo).toMap
    }.getOrElse(Map.empty)

    val compiledFields = compilationResult.andThen(_.map(_.validated).sequence)

    NodeCompilationResult(
      expressionTypingInfo = fieldsTypingInfo,
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, typedObject)).getOrElse(Valid(ctx)),
      compiledObject = compiledFields,
      expressionType = Some(typedObject)
    )
  }

  def compileExpression(expr: graph.expression.Expression,
                        ctx: ValidationContext,
                        expectedType: TypingResult,
                        fieldName: String = DefaultExpressionId,
                        outputVar: Option[OutputVar])
                       (implicit nodeId: NodeId): NodeCompilationResult[api.expression.Expression] = {
    val expressionCompilation = objectParametersExpressionCompiler
      .compile(expr, Some(fieldName), ctx, expectedType)
      .map(typedExpr => ExpressionCompilation(fieldName, Some(typedExpr), Valid(typedExpr.expression)))
      .valueOr ( err => ExpressionCompilation(fieldName, None, Invalid(err)))

    NodeCompilationResult(
      expressionTypingInfo = expressionCompilation.expressionTypingInfo,
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, expressionCompilation.typingResult)).getOrElse(Valid(ctx)),
      compiledObject = expressionCompilation.validated,
      expressionType = Some(expressionCompilation.typingResult)
    )
  }

  def compileProcessor(n: Processor, ctx: ValidationContext)
                     (implicit nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, None)
  }

  def compileEnricher(n: Enricher, ctx: ValidationContext, outputVar: Option[OutputVar]) (implicit nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, outputVar)
  }

  private def compileService(n: ServiceRef, validationContext: ValidationContext, outputVar: Option[OutputVar] = None)(implicit nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    definitions.services.get(n.id) match {
      case Some(objectWithMethodDef) =>
        ServiceCompiler.compile(n, outputVar, objectWithMethodDef, validationContext)
      case None =>
        val error = invalid(MissingService(n.id)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(validationContext), error)
    }
  }

  def compileExceptionHandler(ref: ExceptionHandlerRef)
                             (implicit metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, EspExceptionHandler]) = {
    implicit val nodeId: NodeId = NodeId(NodeTypingInfo.ExceptionHandlerNodeId)
    if (metaData.isSubprocess) {
      //FIXME: what should be here?
      (Map.empty, Valid(new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      }))
    } else {
      createProcessObject[EspExceptionHandler](definitions.exceptionHandlerFactory, ref.parameters, List.empty, outputVariableNameOpt = None, Left(contextWithOnlyGlobalVariables), None, Seq.empty)
    }
  }

  private def nodeValidator(implicit metaData: MetaData)
  = new GenericNodeTransformationValidator(objectParametersExpressionCompiler, expressionConfig)

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext
  = globalVariablesPreparer.emptyValidationContext(metaData)

  private def defaultContextAfter(additionalData: CustomTransformerAdditionalData,
                                  node: CustomNodeData, ending: Boolean,
                                  branchCtx: GenericValidationContext,
                                  nodeDefinition: ObjectWithMethodDef)
                                 (implicit nodeId: NodeId, metaData: MetaData): Option[AnyRef] => ValidatedNel[ProcessCompilationError, ValidationContext] = maybeCNode => {
    val validationContext = branchCtx.left.getOrElse(contextWithOnlyGlobalVariables)
    val maybeClearedContext = if (additionalData.clearsContext) validationContext.clearVariables else validationContext

    def ctxWithVar(outputVar: OutputVar, typ: TypingResult) = maybeClearedContext.withVariable(outputVar, typ)
      //ble... NonEmptyList is invariant...
      .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

    maybeCNode match {
      case None => node.outputVar.map(output => ctxWithVar(OutputVar.customNode(output), Unknown)).getOrElse(Valid(maybeClearedContext))
      case Some(cNode) =>
        (node.outputVar, returnType(nodeDefinition, cNode)) match {
          case (Some(varName), Some(typ)) => ctxWithVar(OutputVar.customNode(varName), typ)
          case (None, None) => Valid(maybeClearedContext)
          case (Some(_), None) => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
          case (None, Some(_)) if ending => Valid(maybeClearedContext)
          case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
        }
    }
  }


  private def getSubprocessParamDefinition(subprocessInput: SubprocessInput, paramName: String): ValidatedNel[PartSubGraphCompilationError, Parameter] = {
    val subParam = subprocessInput.subprocessParams.get.find(_.name == paramName).get
    subParam.typ.toRuntimeClass(classLoader) match {
      case Success(runtimeClass) =>
        valid(Parameter.optional(paramName, Typed(runtimeClass)))
      case Failure(_) =>
        invalid(
          SubprocessParamClassLoadError(paramName, subParam.typ.refClazzName, subprocessInput.id)
        ).toValidatedNel
    }
  }

  //TODO: better classloader error handling
  private def loadFromParameter(subprocessParameter: SubprocessParameter)(implicit nodeId: NodeId) =
    subprocessParameter.typ.toRuntimeClass(classLoader).map(Typed(_)).getOrElse(throw new IllegalArgumentException(
      s"Failed to load subprocess parameter: ${subprocessParameter.typ.refClazzName} for ${nodeId.id}"))

  private def compileObjectWithTransformation[T](data: NodeData with WithParameters,
                                                 ctx: GenericValidationContext,
                                                 outputVar: Option[String],
                                                 nodeDefinition: ObjectWithMethodDef,
                                                 defaultCtxForCreatedObject: Option[T] => ValidatedNel[ProcessCompilationError, ValidationContext])
                                                (implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[T] = {
    val branchParameters = data.cast[Join].map(_.branchParameters).getOrElse(List.empty)
    val parameters = data.parameters
    val generic = validateGenericTransformer(ctx, parameters, branchParameters, outputVar)
    if (generic.isDefinedAt(nodeDefinition)) {
      val afterValidation = generic(nodeDefinition).map {
        case TransformationResult(Nil, computedParameters, outputContext, finalState) =>
          val (typingInfo, validProcessObject) = createProcessObject[T](nodeDefinition, parameters,
            branchParameters, outputVar, ctx, Some(computedParameters), Seq(FinalStateValue(finalState)))
          (typingInfo, Some(computedParameters), outputContext, validProcessObject)
        case TransformationResult(h :: t, computedParameters, outputContext, _) =>
          //TODO: typing info here??
          (Map.empty[String, ExpressionTypingInfo], Some(computedParameters), outputContext, Invalid(NonEmptyList(h, t)))
      }
      val finalParameterList = afterValidation.map(_._2).valueOr(_ => None)
        .map(StandardParameterEnrichment.enrichParameterDefinitions(_, nodeDefinition.objectDefinition.nodeConfig))
      NodeCompilationResult(afterValidation.map(_._1).valueOr(_ => Map.empty), finalParameterList, afterValidation.map(_._3), afterValidation.andThen(_._4))
    } else {
      val (typingInfo, validProcessObject) = createProcessObject[T](nodeDefinition, parameters,
        branchParameters, outputVar, ctx, None, Seq.empty)
      val nextCtx = validProcessObject.fold(_ => defaultCtxForCreatedObject(None), cNode =>
        contextAfterNode(data, cNode, ctx, (c: T) => defaultCtxForCreatedObject(Some(c)))
      )
      NodeCompilationResult(typingInfo, None, nextCtx, validProcessObject)
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


  private def createProcessObject[T](nodeDefinition: ObjectWithMethodDef,
                                     parameters: List[evaluatedparam.Parameter],
                                     branchParameters: List[BranchParameters],
                                     outputVariableNameOpt: Option[String],
                                     ctxOrBranches: GenericValidationContext,
                                     parameterDefinitionsToUse: Option[List[Parameter]],
                                     additionalDependencies: Seq[AnyRef])
                                    (implicit nodeId: NodeId,
                                     metaData: MetaData): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    val ctx = ctxOrBranches.left.getOrElse(contextWithOnlyGlobalVariables)
    val branchContexts = ctxOrBranches.right.getOrElse(Map.empty)

    val compiledObjectWithTypingInfo = objectParametersExpressionCompiler.compileObjectParameters(parameterDefinitionsToUse.getOrElse(nodeDefinition.parameters),
      parameters, branchParameters, ctx, branchContexts, eager = false).andThen { compiledParameters =>
      factory.createObject[T](nodeDefinition, compiledParameters, outputVariableNameOpt, additionalDependencies).map { obj =>
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

  private def contextAfterNode[T](node: NodeData, cNode: T,
                                  validationContexts: GenericValidationContext,
                                  legacy: T => ValidatedNel[ProcessCompilationError, ValidationContext])
                                 (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val contextTransformationDefOpt = cNode.cast[AbstractContextTransformation].map(_.definition)
      (contextTransformationDefOpt, validationContexts) match {
        case (Some(transformation: ContextTransformationDef), Left(validationContext)) =>
          // copying global variables because custom transformation may override them -> todo in ValidationContext
          transformation.transform(validationContext).map(_.copy(globalVariables = validationContext.globalVariables))
        case (Some(transformation: JoinContextTransformationDef), Right(branchEndContexts)) =>
          // copying global variables because custom transformation may override them -> todo in ValidationContext
          transformation.transform(branchEndContexts).map(_.copy(globalVariables = contextWithOnlyGlobalVariables.globalVariables))
        case (Some(transformation), ctx) =>
          Invalid(FatalUnknownError(s"Invalid ContextTransformation class $transformation for contexts: $ctx")).toValidatedNel
        case (None, _) =>
          legacy(cNode)
      }
    }
  }


  private def validateGenericTransformer[T](ctx: GenericValidationContext,
                                            parameters: List[evaluatedparam.Parameter],
                                            branchParameters: List[BranchParameters], outputVar: Option[String])
                                           (implicit metaData: MetaData, nodeId: NodeId):
  PartialFunction[ObjectWithMethodDef, Validated[NonEmptyList[ProcessCompilationError], TransformationResult]] = {
    case nodeDefinition if nodeDefinition.obj.isInstanceOf[SingleInputGenericNodeTransformation[_]] && ctx.isLeft =>
      val transformer = nodeDefinition.obj.asInstanceOf[SingleInputGenericNodeTransformation[_]]
      nodeValidator.validateNode(transformer, parameters, branchParameters, outputVar)(ctx.left.get)
    case nodeDefinition if nodeDefinition.obj.isInstanceOf[JoinGenericNodeTransformation[_]] && ctx.isRight =>
      val transformer = nodeDefinition.obj.asInstanceOf[JoinGenericNodeTransformation[_]]
      nodeValidator.validateNode(transformer, parameters, branchParameters, outputVar)(ctx.right.get)
  }

  //This class is extracted to separate object, as handling service needs serious refactor (see comment in ServiceReturningType), and we don't want
  //methods that will probably be replaced to be mixed with others
  object ServiceCompiler {

    def compile(n: ServiceRef,
                outputVar: Option[OutputVar],
                objWithMethod: ObjectWithMethodDef,
                ctx: ValidationContext)(implicit nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
      val computedParameters = objectParametersExpressionCompiler.compileEagerObjectParameters(objWithMethod.parameters, n.parameters, ctx)
      val outputCtx = outputVar match {
        case Some(output) =>
          NodeValidationExceptionHandler.handleExceptions {
            computeReturnType(objWithMethod, computedParameters)
          }.andThen(ctx.withVariable(output, _))
        case None => Valid(ctx)
      }

      val serviceRef = computedParameters.map { params =>
        compiledgraph.service.ServiceRef(n.id, ServiceInvoker(objWithMethod), params)
      }
      val nodeTypingInfo = computedParameters.map(_.map(p => p.name -> p.typingInfo).toMap).getOrElse(Map.empty)
      NodeCompilationResult(nodeTypingInfo, None, outputCtx, serviceRef)
    }

    //this method tries to compute constant parameters if service is ServiceReturningType
    //TODO: is it right way to do this? Maybe we just need to analyze Expression?
    private def computeReturnType(objWithMethod: ObjectWithMethodDef,
                                  parameters: Validated[_, List[compiledgraph.evaluatedparam.Parameter]])(implicit nodeId: NodeId): TypingResult = objWithMethod.obj match {
      case srt: ServiceReturningType =>
        parameters.map { validParams =>
          val data = validParams.map { param =>
            param.name -> (param.returnType, tryToEvaluateParam(param))
          }.toMap
          srt.returnType(data)
        }.getOrElse(Unknown)
      case _ =>
        objWithMethod.returnType
    }

    /*
        we try to evaluate parameter, but if it fails (e.g. it contains variable), or future does not complete immediately - we just return None
     */
    private def tryToEvaluateParam(param: compiledgraph.evaluatedparam.Parameter)(implicit nodeId: NodeId): Option[Any] = {
      implicit val meta: MetaData = MetaData("", null)
      Try {
        expressionEvaluator.evaluateParameter(param, Context("")).value
      }.toOption
    }

  }
}

