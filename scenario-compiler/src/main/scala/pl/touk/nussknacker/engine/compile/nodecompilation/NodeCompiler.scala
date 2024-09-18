package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel, Writer}
import cats.implicits._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.transformation.{JoinDynamicComponent, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, Source}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.FragmentParameterValidator.validateParameterNames
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.{
  ComponentExecutorFactory,
  ExpressionCompiler,
  FragmentSourceWithTestWithParametersSupportFactory,
  NodeValidationExceptionHandler
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.dynamic.{
  DynamicComponentDefinitionWithImplementation,
  FinalStateValue
}
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.branchParameterExpressionId
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
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

}

class NodeCompiler(
    definitions: ModelDefinition,
    fragmentDefinitionExtractor: FragmentParametersDefinitionExtractor,
    expressionCompiler: ExpressionCompiler,
    classLoader: ClassLoader,
    listeners: Seq[ProcessListener],
    resultCollector: ResultCollector,
    componentUseCase: ComponentUseCase,
    nonServicesLazyParamStrategy: LazyParameterCreationStrategy
) {

  def withLabelsDictTyper: NodeCompiler = {
    new NodeCompiler(
      definitions,
      fragmentDefinitionExtractor,
      expressionCompiler.withLabelsDictTyper,
      classLoader,
      listeners,
      resultCollector,
      componentUseCase,
      nonServicesLazyParamStrategy
    )
  }

  type GenericValidationContext = Either[ValidationContext, Map[String, ValidationContext]]

  private lazy val globalVariablesPreparer          = GlobalVariablesPreparer(expressionConfig)
  private implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  private val expressionConfig: ExpressionConfigDefinition =
    definitions.expressionConfig

  private val parametersEvaluator =
    new ParameterEvaluator(globalVariablesPreparer, listeners)
  private val factory = new ComponentExecutorFactory(parametersEvaluator)
  private val dynamicNodeValidator =
    new DynamicNodeValidator(expressionCompiler, globalVariablesPreparer, parametersEvaluator)
  private val builtInNodeCompiler = new BuiltInNodeCompiler(expressionCompiler)

  def compileSource(
      nodeData: SourceNodeData
  )(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[Source] = nodeData match {
    case a @ Source(_, ref, _) =>
      definitions.getComponent(ComponentType.Source, ref.typ) match {
        case Some(definition) =>
          def defaultCtxForMethodBasedCreatedComponentExecutor(
              returnType: Option[TypingResult]
          ) =
            contextWithOnlyGlobalVariables.withVariable(
              VariableConstants.InputVariableName,
              returnType.getOrElse(Unknown),
              paramName = None
            )

          compileComponentWithContextTransformation[Source](
            a.parameters,
            Nil,
            Left(contextWithOnlyGlobalVariables),
            Some(VariableConstants.InputVariableName),
            definition,
            defaultCtxForMethodBasedCreatedComponentExecutor
          ).map(_._1)
        case None =>
          val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
          // TODO: is this default behaviour ok?
          val defaultCtx =
            contextWithOnlyGlobalVariables.withVariable(VariableConstants.InputVariableName, Unknown, paramName = None)
          NodeCompilationResult(Map.empty, None, defaultCtx, error)
      }
    case frag @ FragmentInputDefinition(id, _, _) =>
      val parameterDefinitions                 = fragmentDefinitionExtractor.extractParametersDefinition(frag)
      val variables: Map[String, TypingResult] = parameterDefinitions.value.map(a => a.name.value -> a.typ).toMap
      val validationContext                    = contextWithOnlyGlobalVariables.copy(localVariables = variables)

      val compilationResult = definitions.getComponent(ComponentType.Fragment, id) match {
        // This case is when fragment is stubbed with test data
        case Some(definition) =>
          compileComponentWithContextTransformation[Source](
            Nil,
            Nil,
            Left(contextWithOnlyGlobalVariables),
            None,
            definition,
            _ => Valid(validationContext)
          ).map(_._1)

        // For default case, we creates source that support test with parameters
        case None =>
          val validatorsCompilationResult = parameterDefinitions.value.flatMap { paramDef =>
            paramDef.validators.map(v =>
              expressionCompiler.compileValidator(v, paramDef.name, paramDef.typ, validationContext.globalVariables)
            )
          }.sequence

          NodeCompilationResult(
            Map.empty,
            None,
            Valid(validationContext),
            validatorsCompilationResult.andThen(_ =>
              Valid(new FragmentSourceWithTestWithParametersSupportFactory(parameterDefinitions.value).createSource())
            )
          )
      }

      val parameterNameValidation = validateParameterNames(parameterDefinitions.value)

      // by relying on name for the field names used on FE, we display the same errors under all fields with the
      // duplicated name
      // TODO: display all errors when switching to field name errors not reliant on parameter name
      val displayUniqueNameReliantErrors = parameterNameValidation.fold(
        errors => !errors.exists(_.isInstanceOf[DuplicateFragmentInputParameter]),
        _ => true
      )

      val displayableErrors = parameterNameValidation |+| {
        if (displayUniqueNameReliantErrors)
          uniqueNameReliantErrors(frag, parameterDefinitions, validationContext)
        else
          Valid(())
      }

      compilationResult.copy(compiledObject = displayableErrors.andThen(_ => compilationResult.compiledObject))
  }

  private def uniqueNameReliantErrors(
      fragmentInputDefinition: FragmentInputDefinition,
      parameterDefinitions: Writer[List[PartSubGraphCompilationError], List[Parameter]],
      validationContext: ValidationContext
  )(implicit nodeId: NodeId) = {
    val parameterExtractionValidation =
      NonEmptyList.fromList(parameterDefinitions.written).map(errors => invalid(errors)).getOrElse(valid(()))

    val fixedValuesErrors = fragmentInputDefinition.parameters
      .map { param =>
        FragmentParameterValidator.validateFixedExpressionValues(
          param,
          validationContext,
          expressionCompiler
        )
      }
      .sequence
      .map(_ => ())

    val dictValueEditorErrors = fragmentInputDefinition.parameters
      .map { param =>
        FragmentParameterValidator.validateValueInputWithDictEditor(param, expressionConfig.dictionaries, classLoader)
      }
      .sequence
      .map(_ => ())

    parameterExtractionValidation |+| fixedValuesErrors |+| dictValueEditorErrors
  }

  def compileCustomNodeObject(data: CustomNodeData, ctx: GenericValidationContext, ending: Boolean)(
      implicit metaData: MetaData,
      nodeId: NodeId
  ): NodeCompilationResult[AnyRef] = {

    val outputVar       = data.outputVar.map(OutputVar.customNode)
    val defaultCtx      = ctx.fold(identity, _ => contextWithOnlyGlobalVariables)
    val defaultCtxToUse = outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.getComponent(ComponentType.CustomComponent, data.nodeType) match {
      case Some(componentDefinition)
          if ending && !componentDefinition.componentTypeSpecificData.asCustomComponentData.canBeEnding =>
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(Set(nodeId.id))))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
      case Some(componentDefinition) =>
        val default = defaultContextAfter(data, ending, ctx)
        compileComponentWithContextTransformation[AnyRef](
          data.parameters,
          data.cast[Join].map(_.branchParameters).getOrElse(Nil),
          ctx,
          outputVar.map(_.outputName),
          componentDefinition,
          default
        ).map(_._1)
      case None =>
        val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
    }
  }

  def compileSink(
      sink: Sink,
      ctx: ValidationContext
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[api.process.Sink] = {
    val ref = sink.ref

    definitions.getComponent(ComponentType.Sink, ref.typ) match {
      case Some(definition) =>
        compileComponentWithContextTransformation[api.process.Sink](
          sink.parameters,
          Nil,
          Left(ctx),
          None,
          definition,
          _ => Valid(ctx)
        ).map(_._1)
      case None =>
        val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(ctx), error)
    }
  }

  def compileFragmentInput(fragmentInput: FragmentInput, ctx: ValidationContext)(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): NodeCompilationResult[List[CompiledParameter]] = {

    val ref            = fragmentInput.ref
    val validParamDefs = fragmentDefinitionExtractor.extractParametersDefinition(fragmentInput)

    val childCtx = ctx.pushNewContext()
    val newCtx =
      validParamDefs.value.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx)) {
        case (acc, paramDef) => acc.andThen(_.withVariable(OutputVar.variable(paramDef.name.value), paramDef.typ))
      }
    val validParams =
      expressionCompiler.compileExecutorComponentNodeParameters(validParamDefs.value, ref.parameters, ctx)
    val validParamsCombinedErrors = validParams.combine(
      NonEmptyList
        .fromList(validParamDefs.written)
        .map(invalid)
        .getOrElse(valid(List.empty[CompiledParameter]))
    )
    val expressionTypingInfo =
      validParams
        .map(_.map(p => p.name.value -> p.typingInfo).toMap)
        .valueOr(_ => Map.empty[String, ExpressionTypingInfo])
    NodeCompilationResult(expressionTypingInfo, None, newCtx, validParamsCombinedErrors)
  }

  // expression is deprecated, will be removed in the future
  def compileSwitch(
      expressionRaw: Option[(String, Expression)],
      choices: List[(String, Expression)],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): NodeCompilationResult[(Option[CompiledExpression], List[CompiledExpression])] = {
    builtInNodeCompiler.compileSwitch(expressionRaw, choices, ctx)
  }

  def compileFilter(filter: Filter, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    builtInNodeCompiler.compileFilter(filter, ctx)
  }

  def compileVariable(variable: Variable, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    builtInNodeCompiler.compileVariable(variable, ctx)
  }

  def fieldToTypedExpression(fields: List[pl.touk.nussknacker.engine.graph.variable.Field], ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Map[String, TypedExpression]] = {
    fields.map { field =>
      expressionCompiler
        .compile(field.expression, Some(ParameterName(field.name)), ctx, Unknown)
        .map(typedExpr => field.name -> typedExpr)
    }
  }.sequence.map(_.toMap)

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      ctx: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {
    builtInNodeCompiler.compileFields(fields, ctx, outputVar)
  }

  def compileProcessor(
      n: Processor,
      ctx: ValidationContext
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, None)
  }

  def compileEnricher(n: Enricher, ctx: ValidationContext, outputVar: OutputVar)(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    compileService(n.service, ctx, Some(outputVar))
  }

  private def compileService(n: ServiceRef, validationContext: ValidationContext, outputVar: Option[OutputVar])(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {

    definitions.getComponent(ComponentType.Service, n.id) match {
      case Some(componentDefinition) if componentDefinition.component.isInstanceOf[EagerService] =>
        compileEagerService(n, componentDefinition, validationContext, outputVar)
      case Some(static: MethodBasedComponentDefinitionWithImplementation) =>
        ServiceCompiler.compile(n, outputVar, static, validationContext)
      case Some(_: DynamicComponentDefinitionWithImplementation) =>
        val error = invalid(
          CustomNodeError(
            "Not supported service implementation: DynamicComponent can be mixed only with EagerService",
            None
          )
        ).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(validationContext), error)
      case Some(notSupportedComponentDefinition) =>
        throw new IllegalStateException(
          s"Not supported ${classOf[ComponentDefinitionWithImplementation].getName}: ${notSupportedComponentDefinition.getClass}"
        )
      case None =>
        val error = invalid(MissingService(n.id)).toValidatedNel
        NodeCompilationResult(Map.empty[String, ExpressionTypingInfo], None, Valid(validationContext), error)
    }
  }

  private def compileEagerService(
      serviceRef: ServiceRef,
      componentDefinition: ComponentDefinitionWithImplementation,
      validationContext: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId, metaData: MetaData): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    val defaultCtxForMethodBasedCreatedComponentExecutor
        : Option[TypingResult] => ValidatedNel[ProcessCompilationError, ValidationContext] = returnTypeOpt =>
      outputVar match {
        case Some(out) =>
          returnTypeOpt
            .map(Valid(_))
            .getOrElse(Invalid(NonEmptyList.of(RedundantParameters(Set(ParameterName("OutputVariable"))))))
            .andThen(validationContext.withVariable(out, _))
        case None => Valid(validationContext)
      }

    def createService(invoker: ServiceInvoker, nodeParams: List[NodeParameter], paramsDefs: List[Parameter]) =
      compiledgraph.service.ServiceRef(
        id = serviceRef.id,
        invoker = invoker,
        resultCollector = resultCollector
      )

    val compilationResult = compileComponentWithContextTransformation[ServiceInvoker](
      parameters = serviceRef.parameters,
      branchParameters = Nil,
      ctx = Left(validationContext),
      outputVar = outputVar.map(_.outputName),
      componentDefinition = componentDefinition,
      defaultCtxForMethodBasedCreatedComponentExecutor = defaultCtxForMethodBasedCreatedComponentExecutor
    )
    compilationResult.map { case (serviceInvoker, nodeParams) =>
      // TODO: Currently in case of object compilation failures we prefer to create "dumb" service invoker, with empty parameters list
      //       instead of return Invalid - I assume that it is probably because of errors accumulation purpose.
      //       We should clean up this compilation process by some NodeCompilationResult refactor like introduction of WriterT monad transformer
      createService(serviceInvoker, nodeParams, compilationResult.parameters.getOrElse(List.empty))
    }
  }

  private def unwrapContextTransformation[T](value: Any): T = (value match {
    case ct: ContextTransformation => ct.implementation
    case a                         => a
  }).asInstanceOf[T]

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(metaData)

  private def defaultContextAfter(
      node: CustomNodeData,
      ending: Boolean,
      branchCtx: GenericValidationContext
  )(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): Option[TypingResult] => ValidatedNel[ProcessCompilationError, ValidationContext] =
    returnTypeOpt => {
      val validationContext = branchCtx.left.getOrElse(contextWithOnlyGlobalVariables)

      def ctxWithVar(outputVar: OutputVar, typ: TypingResult) = validationContext
        .withVariable(outputVar, typ)
        // ble... NonEmptyList is invariant...
        .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

      (node.outputVar, returnTypeOpt) match {
        case (Some(varName), Some(typ)) => ctxWithVar(OutputVar.customNode(varName), typ)
        case (None, None)               => Valid(validationContext)
        case (Some(_), None) => Invalid(NonEmptyList.of(RedundantParameters(Set(ParameterName("OutputVariable")))))
        case (None, Some(_)) if ending => Valid(validationContext)
        case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set(ParameterName("OutputVariable")))))
      }
    }

  private def compileComponentWithContextTransformation[ComponentExecutor](
      parameters: List[NodeParameter],
      branchParameters: List[BranchParameters],
      ctx: GenericValidationContext,
      outputVar: Option[String],
      componentDefinition: ComponentDefinitionWithImplementation,
      defaultCtxForMethodBasedCreatedComponentExecutor: Option[TypingResult] => ValidatedNel[
        ProcessCompilationError,
        ValidationContext
      ]
  )(
      implicit metaData: MetaData,
      nodeId: NodeId
  ): NodeCompilationResult[(ComponentExecutor, List[NodeParameter])] = {
    componentDefinition match {
      case dynamicComponent: DynamicComponentDefinitionWithImplementation =>
        val afterValidation =
          validateDynamicTransformer(ctx, parameters, branchParameters, outputVar, dynamicComponent).map {
            case TransformationResult(Nil, computedParameters, outputContext, finalState, nodeParameters) =>
              val computedParameterNames = computedParameters.filterNot(_.branchParam).map(p => p.name)
              val withoutRedundant       = nodeParameters.filter(p => computedParameterNames.contains(p.name))
              val (typingInfo, validComponentExecutor) = createComponentExecutor[ComponentExecutor](
                componentDefinition,
                withoutRedundant,
                branchParameters,
                outputVar,
                ctx,
                computedParameters,
                Seq(FinalStateValue(finalState))
              )
              (
                typingInfo,
                Some(computedParameters),
                outputContext,
                validComponentExecutor.map((_, withoutRedundant))
              )
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
      case staticComponent: MethodBasedComponentDefinitionWithImplementation =>
        val (typingInfo, validComponentExecutor) = createComponentExecutor[ComponentExecutor](
          componentDefinition,
          parameters,
          branchParameters,
          outputVar,
          ctx,
          staticComponent.parameters,
          Seq.empty
        )
        val nextCtx = validComponentExecutor.fold(
          _ => defaultCtxForMethodBasedCreatedComponentExecutor(staticComponent.returnType),
          executor =>
            contextAfterMethodBasedCreatedComponentExecutor(
              executor,
              ctx,
              (executor: ComponentExecutor) =>
                defaultCtxForMethodBasedCreatedComponentExecutor(returnType(staticComponent.returnType, executor))
            )
        )
        val unwrappedComponentExecutor =
          validComponentExecutor.map(unwrapContextTransformation[ComponentExecutor](_)).map((_, parameters))
        NodeCompilationResult(typingInfo, Some(staticComponent.parameters), nextCtx, unwrappedComponentExecutor)
    }
  }

  private def returnType(definitionReturnType: Option[TypingResult], componentExecutor: Any): Option[TypingResult] =
    componentExecutor match {
      case returningType: ReturningType =>
        Some(returningType.returnType)
      case _ =>
        definitionReturnType
    }

  private def createComponentExecutor[ComponentExecutor](
      componentDefinition: ComponentDefinitionWithImplementation,
      nodeParameters: List[NodeParameter],
      nodeBranchParameters: List[BranchParameters],
      outputVariableNameOpt: Option[String],
      ctxOrBranches: GenericValidationContext,
      parameterDefinitionsToUse: List[Parameter],
      additionalDependencies: Seq[AnyRef]
  )(
      implicit nodeId: NodeId,
      metaData: MetaData
  ): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, ComponentExecutor]) = {
    val ctx            = ctxOrBranches.left.getOrElse(contextWithOnlyGlobalVariables)
    val branchContexts = ctxOrBranches.getOrElse(Map.empty)

    val compiledObjectWithTypingInfo = expressionCompiler
      .compileNodeParameters(
        parameterDefinitionsToUse,
        nodeParameters,
        nodeBranchParameters,
        ctx,
        branchContexts
      )
      .andThen { compiledParameters =>
        factory
          .createComponentExecutor[ComponentExecutor](
            componentDefinition,
            compiledParameters,
            outputVariableNameOpt,
            additionalDependencies,
            componentUseCase,
            nonServicesLazyParamStrategy
          )
          .map { componentExecutor =>
            val typingInfo = compiledParameters.flatMap {
              case (TypedParameter(name, TypedExpression(_, typingInfo)), _) =>
                List(name.value -> typingInfo)
              case (TypedParameter(paramName, TypedExpressionMap(valueByBranch)), _) =>
                valueByBranch.map { case (branch, TypedExpression(_, typingInfo)) =>
                  val expressionId = branchParameterExpressionId(paramName, branch)
                  expressionId -> typingInfo
                }
            }.toMap
            (typingInfo, componentExecutor)
          }
      }
    (compiledObjectWithTypingInfo.map(_._1).valueOr(_ => Map.empty), compiledObjectWithTypingInfo.map(_._2))
  }

  private def contextAfterMethodBasedCreatedComponentExecutor[ComponentExecutor](
      executor: ComponentExecutor,
      validationContexts: GenericValidationContext,
      handleNonContextTransformingExecutor: ComponentExecutor => ValidatedNel[
        ProcessCompilationError,
        ValidationContext
      ]
  )(implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val contextTransformationDefOpt = executor.cast[AbstractContextTransformation].map(_.definition)
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
          handleNonContextTransformingExecutor(executor)
      }
    }
  }

  private def validateDynamicTransformer(
      eitherSingleOrJoin: GenericValidationContext,
      parameters: List[NodeParameter],
      branchParameters: List[BranchParameters],
      outputVar: Option[String],
      dynamicDefinition: DynamicComponentDefinitionWithImplementation
  )(implicit metaData: MetaData, nodeId: NodeId): ValidatedNel[ProcessCompilationError, TransformationResult] =
    (dynamicDefinition.component, eitherSingleOrJoin) match {
      case (single: SingleInputDynamicComponent[_], Left(singleCtx)) =>
        dynamicNodeValidator.validateNode(
          single,
          parameters,
          branchParameters,
          outputVar,
          dynamicDefinition.parametersConfig
        )(
          singleCtx
        )
      case (join: JoinDynamicComponent[_], Right(joinCtx)) =>
        dynamicNodeValidator.validateNode(
          join,
          parameters,
          branchParameters,
          outputVar,
          dynamicDefinition.parametersConfig
        )(
          joinCtx
        )
      case (_: SingleInputDynamicComponent[_], Right(_)) =>
        Invalid(
          NonEmptyList.of(CustomNodeError("Invalid scenario structure: single input component used as a join", None))
        )
      case (_: JoinDynamicComponent[_], Left(_)) =>
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
        objWithMethod: MethodBasedComponentDefinitionWithImplementation,
        ctx: ValidationContext
    )(implicit metaData: MetaData, nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
      val computedParameters =
        expressionCompiler.compileExecutorComponentNodeParameters(objWithMethod.parameters, n.parameters, ctx)
      val outputCtx = outputVar match {
        case Some(output) =>
          objWithMethod.returnType
            .map(Valid(_))
            .getOrElse(Invalid(NonEmptyList.of(RedundantParameters(Set(ParameterName("OutputVariable"))))))
            .andThen(ctx.withVariable(output, _))
        case None => Valid(ctx)
      }

      val serviceRef = computedParameters.map { params =>
        val evaluateParams = (c: Context) => Params(parametersEvaluator.evaluate(params, c)(nodeId, metaData))
        compiledgraph.service.ServiceRef(
          id = n.id,
          invoker = new MethodBasedServiceInvoker(metaData, nodeId, outputVar, objWithMethod, evaluateParams),
          resultCollector = resultCollector
        )
      }
      val nodeTypingInfo = computedParameters.map(_.map(p => p.name.value -> p.typingInfo).toMap).getOrElse(Map.empty)
      NodeCompilationResult(nodeTypingInfo, None, outputCtx, serviceRef)
    }

  }

}
