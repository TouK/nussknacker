package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, invalid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{JoinGenericNodeTransformation, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.expression.{ExpressionTypingInfo, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo, NodeValidationExceptionHandler, ProcessObjectFactory}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{FinalStateValue, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ProcessDefinition}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.{evaluatedparam, node}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{Interpreter, api}
import shapeless.Typeable
import shapeless.syntax.typeable._

class NodeCompiler(definitions: ProcessDefinition[ObjectWithMethodDef],
                   objectParametersExpressionCompiler: ExpressionCompiler,
                   classLoader: ClassLoader) {

  type GenericValidationContext = Either[ValidationContext, Map[String, ValidationContext]]

  private lazy val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  private val expressionConfig: ProcessDefinitionExtractor.ExpressionDefinition[ObjectWithMethodDef] = definitions.expressionConfig

  //FIXME: should it be here?
  private val expressionEvaluator =
    ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(expressionConfig))
  private val factory: ProcessObjectFactory = new ProcessObjectFactory(expressionEvaluator)

  def compileSource(nodeData: SourceNodeData)(implicit metaData: MetaData, nodeId: NodeId): (Map[String, ExpressionTypingInfo],
    Option[List[Parameter]], ValidatedNel[ProcessCompilationError, ValidationContext],
    ValidatedNel[ProcessCompilationError, Source[_]]) = nodeData match {
    case a@pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
      definitions.sourceFactories.get(ref.typ) match {
        case Some(definition) =>
          def defaultContextTransformation(compiled: Option[Any]) =
            contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, compiled.flatMap(a => returnType(definition, a)).getOrElse(Unknown))

          compileObjectWithTransformation[Source[_]](a, Left(contextWithOnlyGlobalVariables), Some(Interpreter.InputParamName), definition, defaultContextTransformation)
        case None =>
          val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
          //TODO: is this default behaviour ok?
          val defaultCtx = contextWithOnlyGlobalVariables.withVariable(Interpreter.InputParamName, Unknown)
          (Map.empty, None, defaultCtx, error)
      }
    case SubprocessInputDefinition(_, params, _) =>
      (Map.empty, None, Valid(contextWithOnlyGlobalVariables.copy(localVariables = params.map(p => p.name -> loadFromParameter(p)).toMap)), Valid(new Source[Any] {}))
  }

  def compileCustomNodeObject(data: CustomNodeData, ctx: GenericValidationContext, ending: Boolean)
                             (implicit metaData: MetaData, nodeId: NodeId):
  (Map[String, ExpressionTypingInfo], Option[List[Parameter]], ValidatedNel[ProcessCompilationError, ValidationContext], ValidatedNel[ProcessCompilationError, AnyRef]) = {
    val outputVar = data.outputVar

    val defaultCtx = ctx.fold(identity, _ => contextWithOnlyGlobalVariables)
    val defaultCtxToUse = data.outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.customStreamTransformers.get(data.nodeType) match {
      case Some((_, additionalData)) if ending && !additionalData.canBeEnding =>
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(nodeId.id)))
        (Map.empty, None, defaultCtxToUse, error)
      case Some((nodeDefinition, additionalData)) =>
        val default = defaultContextAfter(additionalData, data, ending, ctx, nodeDefinition)
        compileObjectWithTransformation(data, ctx, outputVar, nodeDefinition, default)
      case None =>
        val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
        (Map.empty, None, defaultCtxToUse, error)
    }
  }

  def compileSink(sink: node.Sink, ctx: ValidationContext)
                 (implicit nodeId: NodeId,
                  metaData: MetaData): (Map[String, ExpressionTypingInfo], Option[List[Parameter]], ValidatedNel[ProcessCompilationError, api.process.Sink]) = {
    val ref = sink.ref
    definitions.sinkFactories.get(ref.typ) match {
      case Some(definition) =>
        val (typeInfo, parameters, _, validSinkFactory) =
          compileObjectWithTransformation[api.process.Sink](sink, Left(ctx), None, definition._1, (_: Any) => Valid(ctx))
        (typeInfo, parameters, validSinkFactory)
      case None =>
        val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
        (Map.empty[String, ExpressionTypingInfo], None, error)
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


  //TODO: better classloader error handling
  private def loadFromParameter(subprocessParameter: SubprocessParameter)(implicit nodeId: NodeId) =
    subprocessParameter.typ.toRuntimeClass(classLoader).map(Typed(_)).getOrElse(throw new IllegalArgumentException(
      s"Failed to load subprocess parameter: ${subprocessParameter.typ.refClazzName} for ${nodeId.id}"))

  private def compileObjectWithTransformation[T](data: NodeData with WithParameters,
                                                 ctx: GenericValidationContext,
                                                 outputVar: Option[String],
                                                 nodeDefinition: ObjectWithMethodDef,
                                                 defaultCtxForCreatedObject: Option[T] => ValidatedNel[ProcessCompilationError, ValidationContext])
                                                (implicit metaData: MetaData, nodeId: NodeId):
  (Map[String, ExpressionTypingInfo], Option[List[Parameter]], Validated[NonEmptyList[ProcessCompilationError], ValidationContext], ValidatedNel[ProcessCompilationError, T]) = {
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
      (afterValidation.map(_._1).valueOr(_ => Map.empty), afterValidation.map(_._2).valueOr(_ => None), afterValidation.map(_._3), afterValidation.andThen(_._4))
    } else {
      val (typingInfo, validProcessObject) = createProcessObject[T](nodeDefinition, parameters,
        branchParameters, outputVar, ctx, None, Seq.empty)
      val nextCtx = validProcessObject.fold(_ => defaultCtxForCreatedObject(None), cNode =>
        contextAfterNode(data, cNode, ctx, (c: T) => defaultCtxForCreatedObject(Some(c)))
      )
      (typingInfo, None, nextCtx, validProcessObject)
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

}
