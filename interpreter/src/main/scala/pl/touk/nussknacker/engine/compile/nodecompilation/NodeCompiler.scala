package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.toTraverseOps
import cats.instances.list._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{
  JoinGenericNodeTransformation,
  SingleInputGenericNodeTransformation
}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.{
  ExpressionParser,
  ExpressionTypingInfo,
  TypedExpression,
  TypedExpressionMap
}
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, Source}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.{ExpressionCompilation, NodeCompilationResult}
import pl.touk.nussknacker.engine.compile.{
  ExpressionCompiler,
  NodeValidationExceptionHandler,
  ProcessObjectFactory,
  StubbedFragmentInputTestSource
}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{
  FinalStateValue,
  GenericNodeTransformationMethodDef,
  ObjectWithMethodDef,
  StandardObjectWithMethodDef
}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{
  DefaultServiceInvoker,
  FragmentComponentDefinitionExtractor,
  ProcessDefinitionExtractor
}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.{DefaultExpressionId, branchParameterExpressionId}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.{evaluatedparam, node}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{api, compiledgraph}
import shapeless.Typeable
import shapeless.syntax.typeable._

object NodeCompiler {

  case class NodeCompilationResult[T](
      expressionTypingInfo: Map[String, ExpressionTypingInfo],
      parameters: Option[List[Parameter]],
      validationContext: ValidatedNel[ProcessCompilationError, ValidationContext],
      compiledObject: ValidatedNel[ProcessCompilationError, T],
      expressionType: Option[TypingResult] = None
  ) {
    def errors: List[ProcessCompilationError] =
      (validationContext.swap.toList ++ compiledObject.swap.toList).flatMap(_.toList)

    def map[R](f: T => R): NodeCompilationResult[R] = copy(compiledObject = compiledObject.map(f))

  }

  private case class ExpressionCompilation[R](
      fieldName: String,
      typedExpression: Option[TypedExpression],
      validated: ValidatedNel[ProcessCompilationError, R]
  ) {

    val typingResult: TypingResult =
      typedExpression.map(_.returnType).getOrElse(Unknown)

    val expressionTypingInfo: Map[String, ExpressionTypingInfo] =
      typedExpression.map(te => (fieldName, te.typingInfo)).toMap
  }

}

class NodeCompiler(
    definitions: ProcessDefinition[ObjectWithMethodDef],
    fragmentDefinitionExtractor: FragmentComponentDefinitionExtractor,
    objectParametersExpressionCompiler: ExpressionCompiler,
    classLoader: ClassLoader,
    resultCollector: ResultCollector,
    componentUseCase: ComponentUseCase
) {

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): NodeCompiler = {
    new NodeCompiler(
      definitions,
      fragmentDefinitionExtractor,
      objectParametersExpressionCompiler.withExpressionParsers(modify),
      classLoader,
      resultCollector,
      componentUseCase
    )
  }

  type GenericValidationContext = Either[ValidationContext, Map[String, ValidationContext]]

  private lazy val globalVariablesPreparer          = GlobalVariablesPreparer(expressionConfig)
  private implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  private val expressionConfig: ProcessDefinitionExtractor.ExpressionDefinition[ObjectWithMethodDef] =
    definitions.expressionConfig

  // FIXME: should it be here?
  private val expressionEvaluator =
    ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(expressionConfig))
  private val factory: ProcessObjectFactory = new ProcessObjectFactory(expressionEvaluator)
  private val nodeValidator =
    new GenericNodeTransformationValidator(objectParametersExpressionCompiler, expressionConfig)

  def compileSource(
      nodeData: SourceNodeData
  )(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[Source] = nodeData match {
    case a @ pl.touk.nussknacker.engine.graph.node.Source(_, ref, _) =>
      definitions.sourceFactories.get(ref.typ) match {
        case Some(definition) =>
          def defaultContextTransformation(compiled: Option[Any]) =
            contextWithOnlyGlobalVariables.withVariable(
              VariableConstants.InputVariableName,
              compiled.flatMap(a => returnType(definition, a)).getOrElse(Unknown),
              paramName = None
            )

          compileObjectWithTransformation[Source](
            a.parameters,
            Nil,
            Left(contextWithOnlyGlobalVariables),
            Some(VariableConstants.InputVariableName),
            definition,
            defaultContextTransformation
          ).map(_._1)
        case None =>
          val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
          // TODO: is this default behaviour ok?
          val defaultCtx =
            contextWithOnlyGlobalVariables.withVariable(VariableConstants.InputVariableName, Unknown, paramName = None)
          NodeCompilationResult(Map.empty, None, defaultCtx, error)
      }
    case frag @ FragmentInputDefinition(id, params, _) =>
      definitions.sourceFactories.get(id) match {
        case Some(definition) =>
          val parameters                           = fragmentDefinitionExtractor.extractParametersDefinition(frag).value
          val variables: Map[String, TypingResult] = parameters.map(a => a.name -> a.typ).toMap
          val validationContext = Valid(
            contextWithOnlyGlobalVariables.copy(localVariables =
              contextWithOnlyGlobalVariables.globalVariables ++ variables
            )
          )

          compileObjectWithTransformation[Source](
            Nil,
            Nil,
            Left(contextWithOnlyGlobalVariables),
            None,
            definition,
            _ => validationContext
          ).map(_._1)
        case None =>
          NodeCompilationResult(
            Map.empty,
            None,
            Valid(
              contextWithOnlyGlobalVariables.copy(localVariables =
                params.map(p => p.name -> loadFromParameter(p)).toMap
              )
            ),
            Valid(new StubbedFragmentInputTestSource(frag, fragmentDefinitionExtractor).createSource())
          )
      }
  }

  def compileCustomNodeObject(data: CustomNodeData, ctx: GenericValidationContext, ending: Boolean)(
      implicit metaData: MetaData,
      nodeId: NodeId
  ): NodeCompilationResult[AnyRef] = {

    val outputVar       = data.outputVar.map(OutputVar.customNode)
    val defaultCtx      = ctx.fold(identity, _ => contextWithOnlyGlobalVariables)
    val defaultCtxToUse = outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.customStreamTransformers.get(data.nodeType) match {
      case Some((_, additionalData)) if ending && !additionalData.canBeEnding =>
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(nodeId.id)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
      case Some((nodeDefinition, _)) =>
        val default = defaultContextAfter(data, ending, ctx, nodeDefinition)
        compileObjectWithTransformation(
          data.parameters,
          data.cast[Join].map(_.branchParameters).getOrElse(Nil),
          ctx,
          outputVar.map(_.outputName),
          nodeDefinition,
          default
        ).map(_._1)
      case None =>
        val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
    }
  }

  def compileSink(
      sink: node.Sink,
      ctx: ValidationContext
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[api.process.Sink] = {
    val ref = sink.ref

    definitions.sinkFactories.get(ref.typ) match {
      case Some(definition) =>
        compileObjectWithTransformation[api.process.Sink](
          sink.parameters,
          Nil,
          Left(ctx),
          None,
          definition,
          (_: Any) => Valid(ctx)
        ).map(_._1)
      case None =>
        val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(ctx), error)
    }
  }

  def compileFragmentInput(fragmentInput: FragmentInput, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[List[compiledgraph.evaluatedparam.Parameter]] = {

    val ref            = fragmentInput.ref
    val validParamDefs = fragmentDefinitionExtractor.extractParametersDefinition(fragmentInput)

    val childCtx = ctx.pushNewContext()
    val newCtx =
      validParamDefs.value.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx)) {
        case (acc, paramDef) => acc.andThen(_.withVariable(OutputVar.variable(paramDef.name), paramDef.typ))
      }
    val validParams =
      objectParametersExpressionCompiler.compileEagerObjectParameters(validParamDefs.value, ref.parameters, ctx)
    val validParamsCombinedErrors = validParams.combine(
      NonEmptyList
        .fromList(validParamDefs.written)
        .map(invalid)
        .getOrElse(valid(List.empty[compiledgraph.evaluatedparam.Parameter]))
    )
    val expressionTypingInfo =
      validParams.map(_.map(p => p.name -> p.typingInfo).toMap).valueOr(_ => Map.empty[String, ExpressionTypingInfo])
    NodeCompilationResult(expressionTypingInfo, None, newCtx, validParamsCombinedErrors)
  }

  // expression is deprecated, will be removed in the future
  def compileSwitch(
      expressionRaw: Option[(String, Expression)],
      choices: List[(String, Expression)],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): NodeCompilationResult[(Option[api.expression.Expression], List[api.expression.Expression])] = {

    // the frontend uses empty string to delete deprecated expression.
    val expression = expressionRaw.filterNot(_._1.isEmpty)

    val expressionCompilation = expression.map { case (output, expression) =>
      compileExpression(expression, ctx, Unknown, NodeExpressionId.DefaultExpressionId, Some(OutputVar.switch(output)))
    }
    val objExpression = expressionCompilation.map(_.compiledObject.map(Some(_))).getOrElse(Valid(None))

    val caseCtx = expressionCompilation.flatMap(_.validationContext.toOption).getOrElse(ctx)
    val caseExpressions = choices.map { case (outEdge, caseExpr) =>
      compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdge, None)
    }
    val expressionTypingInfos = caseExpressions
      .map(_.expressionTypingInfo)
      .foldLeft(expressionCompilation.map(_.expressionTypingInfo).getOrElse(Map.empty)) {
        _ ++ _
      }

    val objCases = caseExpressions.map(_.compiledObject).sequence

    NodeCompilationResult(
      expressionTypingInfos,
      None,
      expressionCompilation.map(_.validationContext).getOrElse(Valid(ctx)),
      objExpression.product(objCases),
      expressionCompilation.flatMap(_.expressionType)
    )
  }

  def fieldToTypedExpression(fields: List[pl.touk.nussknacker.engine.graph.variable.Field], ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Map[String, TypedExpression]] = {
    fields.map { field =>
      objectParametersExpressionCompiler
        .compile(field.expression, Some(field.name), ctx, Unknown)
        .map(typedExpr => field.name -> typedExpr)
    }
  }.sequence.map(_.toMap)

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      ctx: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {
    val compilationResult
        : ValidatedNel[ProcessCompilationError, List[ExpressionCompilation[compiledgraph.variable.Field]]] =
      fields.map { field =>
        objectParametersExpressionCompiler
          .compile(field.expression, Some(field.name), ctx, Unknown)
          .map(typedExpression =>
            ExpressionCompilation(
              field.name,
              Some(typedExpression),
              Valid(compiledgraph.variable.Field(field.name, typedExpression.expression))
            )
          )
      }.sequence

    val typedObject = compilationResult
      .map { fieldsComp =>
        TypedObjectTypingResult(fieldsComp.map(f => (f.fieldName, f.typingResult)).toMap)
      }
      .valueOr(_ => Unknown)

    val fieldsTypingInfo = compilationResult
      .map { compilations =>
        compilations.flatMap(_.expressionTypingInfo).toMap
      }
      .getOrElse(Map.empty)

    val compiledFields = compilationResult.andThen(_.map(_.validated).sequence)

    NodeCompilationResult(
      expressionTypingInfo = fieldsTypingInfo,
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, typedObject)).getOrElse(Valid(ctx)),
      compiledObject = compiledFields,
      expressionType = Some(typedObject)
    )
  }

  def compileExpression(
      expr: Expression,
      ctx: ValidationContext,
      expectedType: TypingResult,
      fieldName: String = DefaultExpressionId,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[api.expression.Expression] = {
    val expressionCompilation = objectParametersExpressionCompiler
      .compile(expr, Some(fieldName), ctx, expectedType)
      .map(typedExpr => ExpressionCompilation(fieldName, Some(typedExpr), Valid(typedExpr.expression)))
      .valueOr(err => ExpressionCompilation(fieldName, None, Invalid(err)))

    NodeCompilationResult(
      expressionTypingInfo = expressionCompilation.expressionTypingInfo,
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, expressionCompilation.typingResult)).getOrElse(Valid(ctx)),
      compiledObject = expressionCompilation.validated,
      expressionType = Some(expressionCompilation.typingResult)
    )
  }

  def compileProcessor(
      n: Processor,
      ctx: ValidationContext
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, None)
  }

  def compileEnricher(n: Enricher, ctx: ValidationContext, outputVar: Option[OutputVar])(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, outputVar)
  }

  def compileService(n: ServiceRef, validationContext: ValidationContext, outputVar: Option[OutputVar])(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {

    definitions.services.get(n.id) match {
      case Some(objectWithMethodDef) if objectWithMethodDef.obj.isInstanceOf[EagerService] =>
        compileEagerService(n, objectWithMethodDef, validationContext, outputVar)
      case Some(static: StandardObjectWithMethodDef) =>
        ServiceCompiler.compile(n, outputVar, static, validationContext)
      case Some(_: GenericNodeTransformationMethodDef) =>
        val error = invalid(
          CustomNodeError(
            "Not supported service implementation: GenericNodeTransformation can be mixed only with EagerService",
            None
          )
        ).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(validationContext), error)
      case None =>
        val error = invalid(MissingService(n.id)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(validationContext), error)
    }
  }

  private def compileEagerService(
      serviceRef: ServiceRef,
      objectWithMethodDef: ObjectWithMethodDef,
      validationContext: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    val ctx: Option[_] => ValidatedNel[ProcessCompilationError, ValidationContext] = invoker =>
      outputVar match {
        case Some(out) =>
          objectWithMethodDef.returnType
            .map(Valid(_))
            .getOrElse(Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable")))))
            .andThen(validationContext.withVariable(out, _))
        case None => Valid(validationContext)
      }

    def prepareCompiledLazyParameters(paramsDefs: List[Parameter], nodeParams: List[evaluatedparam.Parameter]) = {
      val nodeParamsMap = nodeParams.map(p => p.name -> p).toMap
      paramsDefs.collect {
        case paramDef if paramDef.isLazyParameter =>
          val compiledParam = (for {
            param <- nodeParamsMap.get(paramDef.name)
            compiled <- objectParametersExpressionCompiler
              .compileParam(param, validationContext, paramDef, eager = false)
              .toOption
              .flatMap(_.typedValue.cast[TypedExpression])
          } yield compiled)
            .getOrElse(throw new IllegalArgumentException(s"$paramDef is not defined as TypedExpression"))
          compiledgraph.evaluatedparam.Parameter(compiledParam, paramDef)
      }
    }

    def makeInvoker(service: ServiceInvoker, nodeParams: List[evaluatedparam.Parameter], paramsDefs: List[Parameter]) =
      compiledgraph.service.ServiceRef(
        serviceRef.id,
        service,
        prepareCompiledLazyParameters(paramsDefs, nodeParams),
        resultCollector
      )

    val compilationResult = compileObjectWithTransformation[ServiceInvoker](
      serviceRef.parameters,
      Nil,
      Left(validationContext),
      outputVar.map(_.outputName),
      objectWithMethodDef,
      ctx
    )
    compilationResult.map { case (invoker, nodeParams) =>
      // TODO: Currently in case of object compilation failures we prefer to create "dumb" service invoker, with empty parameters list
      //       instead of return Invalid - I assume that it is probably because of errors accumulation purpose.
      //       We should clean up this compilation process by some NodeCompilationResult refactor like introduction of WriterT monad transformer
      makeInvoker(invoker, nodeParams, compilationResult.parameters.getOrElse(List.empty))
    }
  }

  def unwrapContextTransformation[T](value: Any): T = (value match {
    case ct: ContextTransformation => ct.implementation
    case a                         => a
  }).asInstanceOf[T]

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext =
    globalVariablesPreparer.emptyValidationContext(metaData)

  private def defaultContextAfter(
      node: CustomNodeData,
      ending: Boolean,
      branchCtx: GenericValidationContext,
      nodeDefinition: ObjectWithMethodDef
  )(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): Option[AnyRef] => ValidatedNel[ProcessCompilationError, ValidationContext] = maybeValidNode => {
    val validationContext = branchCtx.left.getOrElse(contextWithOnlyGlobalVariables)

    def ctxWithVar(outputVar: OutputVar, typ: TypingResult) = validationContext
      .withVariable(outputVar, typ)
      // ble... NonEmptyList is invariant...
      .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

    maybeValidNode match {
      case None =>
        // we add output variable with Unknown type in case if we have invalid node here - it is to for situation when CustomTransformer.execute end up with failure and we don't want to validation fail fast
        // real checking of output variable was done in NodeValidationExceptionHandler
        val maybeAddedFallbackOutputVariable =
          node.outputVar.map(output => ctxWithVar(OutputVar.customNode(output), Unknown))
        maybeAddedFallbackOutputVariable.getOrElse(Valid(validationContext))
      case Some(validNode) =>
        (node.outputVar, returnType(nodeDefinition, validNode)) match {
          case (Some(varName), Some(typ)) => ctxWithVar(OutputVar.customNode(varName), typ)
          case (None, None)               => Valid(validationContext)
          case (Some(_), None)            => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
          case (None, Some(_)) if ending  => Valid(validationContext)
          case (None, Some(_))            => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
        }
    }
  }

  // TODO: better classloader error handling
  private def loadFromParameter(fragmentParameter: FragmentParameter)(implicit nodeId: NodeId) =
    fragmentParameter.typ
      .toRuntimeClass(classLoader)
      .map(Typed(_))
      .getOrElse(
        throw new IllegalArgumentException(
          s"Failed to load scenario fragment parameter: ${fragmentParameter.typ.refClazzName} for ${nodeId.id}"
        )
      )

  private def compileObjectWithTransformation[T](
      parameters: List[evaluatedparam.Parameter],
      branchParameters: List[evaluatedparam.BranchParameters],
      ctx: GenericValidationContext,
      outputVar: Option[String],
      nodeDefinition: ObjectWithMethodDef,
      defaultCtxForCreatedObject: Option[T] => ValidatedNel[ProcessCompilationError, ValidationContext]
  )(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[(T, List[evaluatedparam.Parameter])] = {
    nodeDefinition match {
      case generic: GenericNodeTransformationMethodDef =>
        val afterValidation = validateGenericTransformer(ctx, parameters, branchParameters, outputVar, generic).map {
          case TransformationResult(Nil, computedParameters, outputContext, finalState, nodeParameters) =>
            val computedParameterNames = computedParameters.filterNot(_.branchParam).map(p => p.name)
            val withoutRedundant       = nodeParameters.filter(p => computedParameterNames.contains(p.name))
            val (typingInfo, validProcessObject) = createProcessObject[T](
              nodeDefinition,
              withoutRedundant,
              branchParameters,
              outputVar,
              ctx,
              computedParameters,
              Seq(FinalStateValue(finalState))
            )
            (typingInfo, Some(computedParameters), outputContext, validProcessObject.map((_, withoutRedundant)))
          case TransformationResult(h :: t, computedParameters, outputContext, _, _) =>
            // TODO: typing info here??
            (
              Map.empty[String, ExpressionTypingInfo],
              Some(computedParameters),
              outputContext,
              Invalid(NonEmptyList(h, t))
            )
        }
        NodeCompilationResult(
          afterValidation.map(_._1).valueOr(_ => Map.empty),
          afterValidation.map(_._2).valueOr(_ => None),
          afterValidation.map(_._3),
          afterValidation.andThen(_._4)
        )
      case static: StandardObjectWithMethodDef =>
        val (typingInfo, validProcessObject) = createProcessObject[T](
          nodeDefinition,
          parameters,
          branchParameters,
          outputVar,
          ctx,
          static.parameters,
          Seq.empty
        )
        val nextCtx = validProcessObject.fold(
          _ => defaultCtxForCreatedObject(None),
          cNode => contextAfterNode(cNode, ctx, (c: T) => defaultCtxForCreatedObject(Some(c)))
        )
        val unwrappedProcessObject = validProcessObject.map(unwrapContextTransformation[T](_)).map((_, parameters))
        NodeCompilationResult(typingInfo, Some(static.parameters), nextCtx, unwrappedProcessObject)
    }
  }

  private def returnType(nodeDefinition: ObjectWithMethodDef, obj: Any): Option[TypingResult] =
    obj match {
      case returningType: ReturningType =>
        Some(returningType.returnType)
      case _ =>
        nodeDefinition.returnType
    }

  private def createProcessObject[T](
      nodeDefinition: ObjectWithMethodDef,
      parameters: List[evaluatedparam.Parameter],
      branchParameters: List[BranchParameters],
      outputVariableNameOpt: Option[String],
      ctxOrBranches: GenericValidationContext,
      parameterDefinitionsToUse: List[Parameter],
      additionalDependencies: Seq[AnyRef]
  )(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    val ctx            = ctxOrBranches.left.getOrElse(contextWithOnlyGlobalVariables)
    val branchContexts = ctxOrBranches.getOrElse(Map.empty)

    val compiledObjectWithTypingInfo = objectParametersExpressionCompiler
      .compileObjectParameters(
        parameterDefinitionsToUse,
        parameters,
        branchParameters,
        ctx,
        branchContexts,
        eager = false
      )
      .andThen { compiledParameters =>
        factory
          .createObject[T](
            nodeDefinition,
            compiledParameters,
            outputVariableNameOpt,
            additionalDependencies,
            componentUseCase
          )
          .map { obj =>
            val typingInfo = compiledParameters.flatMap {
              case (TypedParameter(name, TypedExpression(_, _, typingInfo)), _) =>
                List(name -> typingInfo)
              case (TypedParameter(paramName, TypedExpressionMap(valueByBranch)), _) =>
                valueByBranch.map { case (branch, TypedExpression(_, _, typingInfo)) =>
                  val expressionId = branchParameterExpressionId(paramName, branch)
                  expressionId -> typingInfo
                }
            }.toMap
            (typingInfo, obj)
          }
      }
    (compiledObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), compiledObjectWithTypingInfo.map(_._2))
  }

  private def contextAfterNode[T](
      cNode: T,
      validationContexts: GenericValidationContext,
      legacy: T => ValidatedNel[ProcessCompilationError, ValidationContext]
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val contextTransformationDefOpt = cNode.cast[AbstractContextTransformation].map(_.definition)
      (contextTransformationDefOpt, validationContexts) match {
        case (Some(transformation: ContextTransformationDef), Left(validationContext)) =>
          // copying global variables because custom transformation may override them -> TODO: in ValidationContext
          transformation.transform(validationContext).map(_.copy(globalVariables = validationContext.globalVariables))
        case (Some(transformation: JoinContextTransformationDef), Right(branchEndContexts)) =>
          // copying global variables because custom transformation may override them -> TODO: in ValidationContext
          transformation
            .transform(branchEndContexts)
            .map(_.copy(globalVariables = contextWithOnlyGlobalVariables.globalVariables))
        case (Some(transformation), ctx) =>
          Invalid(
            FatalUnknownError(s"Invalid ContextTransformation class $transformation for contexts: $ctx")
          ).toValidatedNel
        case (None, _) =>
          legacy(cNode)
      }
    }
  }

  private def validateGenericTransformer(
      eitherSingleOrJoin: GenericValidationContext,
      parameters: List[evaluatedparam.Parameter],
      branchParameters: List[BranchParameters],
      outputVar: Option[String],
      genericDefinition: GenericNodeTransformationMethodDef
  )(implicit metaData: MetaData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, TransformationResult] =
    (genericDefinition.obj, eitherSingleOrJoin) match {
      case (single: SingleInputGenericNodeTransformation[_], Left(singleCtx)) =>
        nodeValidator.validateNode(single, parameters, branchParameters, outputVar, genericDefinition.componentConfig)(
          singleCtx
        )
      case (join: JoinGenericNodeTransformation[_], Right(joinCtx)) =>
        nodeValidator.validateNode(join, parameters, branchParameters, outputVar, genericDefinition.componentConfig)(
          joinCtx
        )
      case (_: SingleInputGenericNodeTransformation[_], Right(_)) =>
        Invalid(
          NonEmptyList.of(CustomNodeError("Invalid scenario structure: single input component used as a join", None))
        )
      case (_: JoinGenericNodeTransformation[_], Left(_)) =>
        Invalid(
          NonEmptyList.of(
            CustomNodeError("Invalid scenario structure: join component used as with single, not named input", None)
          )
        )
    }

  // This class is extracted to separate object, as handling service needs serious refactor (see comment in ServiceReturningType), and we don't want
  // methods that will probably be replaced to be mixed with others
  object ServiceCompiler {

    def compile(
        n: ServiceRef,
        outputVar: Option[OutputVar],
        objWithMethod: StandardObjectWithMethodDef,
        ctx: ValidationContext
    )(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
      val computedParameters =
        objectParametersExpressionCompiler.compileEagerObjectParameters(objWithMethod.parameters, n.parameters, ctx)
      val outputCtx = outputVar match {
        case Some(output) =>
          objWithMethod.returnType
            .map(Valid(_))
            .getOrElse(Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable")))))
            .andThen(ctx.withVariable(output, _))
        case None => Valid(ctx)
      }

      val serviceRef = computedParameters.map { params =>
        compiledgraph.service.ServiceRef(
          n.id,
          DefaultServiceInvoker(metaData, nodeId, outputVar, objWithMethod),
          params,
          resultCollector
        )
      }
      val nodeTypingInfo = computedParameters.map(_.map(p => p.name -> p.typingInfo).toMap).getOrElse(Map.empty)
      NodeCompilationResult(nodeTypingInfo, None, outputCtx, serviceRef)
    }

  }

}
