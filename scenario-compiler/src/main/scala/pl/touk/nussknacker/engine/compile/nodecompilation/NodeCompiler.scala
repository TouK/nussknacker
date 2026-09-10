package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data._
import cats.data.Validated.{invalid, valid, Invalid, Valid}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{api, compiledgraph, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonize.MissingSinkHandler
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.ComponentExecutorFactory.ComponentExecutorDependencies
import pl.touk.nussknacker.engine.compile.ExpressionCompiler.CompiledNodeParameters
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.{
  EnricherCompilationResult,
  MockExpressionParameterName,
  NodeCompilationResult
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.DynamicComponentInvocationContext
import pl.touk.nussknacker.engine.definition.component.dynamic.{
  DynamicComponentDefinitionWithImplementation,
  FinalStateValue
}
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.expression.parse.{
  CompiledExpression,
  MultipleBranchesTypedValue,
  SingleBranchTypedValue,
  TypedExpression
}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.branchParameterExpressionId
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

object NodeCompiler {

  val MockExpressionParameterName: ParameterName = ParameterName("$mockExpression")

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

  final case class EnricherCompilationResult(
      serviceRef: compiledgraph.service.ServiceRef,
      mockOutputExpression: Option[CompiledExpression]
  )

}

class NodeCompiler(
    definitions: ModelDefinition,
    fragmentDefinitionExtractor: FragmentParametersDefinitionExtractor,
    expressionCompiler: ExpressionCompiler,
    classLoader: ClassLoader,
    listeners: Seq[ProcessListener],
    resultCollector: ResultCollector,
    runtimeMode: RuntimeMode,
    nodesDeploymentData: NodesDeploymentData,
    nonServicesLazyParamStrategy: LazyParameterCreationStrategy,
) extends LazyLogging {

  def missingSinkHandler(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): MissingSinkHandler = {
    if (scenarioIsAllowedToEndWithoutSink) MissingSinkHandler.AllowMissingSinkHandler
    else MissingSinkHandler.DoNotAllowMissingSinkHandler
  }

  private def scenarioIsAllowedToEndWithoutSink(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ) = {
    import scenarioCompilationDependencies._
    lazy val allowEndingScenarioWithoutSink = definitions.allowEndingScenarioWithoutSink
    lazy val isFragment                     = metaData.typeSpecificData.isFragment
    allowEndingScenarioWithoutSink && !isFragment
  }

  def withLabelsDictTyper: NodeCompiler = {
    new NodeCompiler(
      definitions,
      fragmentDefinitionExtractor,
      expressionCompiler.withLabelsDictTyper,
      classLoader,
      listeners,
      resultCollector,
      runtimeMode,
      nodesDeploymentData,
      nonServicesLazyParamStrategy,
    )
  }

  private val expressionConfig: ExpressionConfigDefinition =
    definitions.expressionConfig
  private val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private val staticComponentOutputValidationContextDeterminer =
    new StaticComponentOutputValidationContextDeterminer(globalVariablesPreparer)

  private val parametersEvaluator =
    ParameterEvaluator(globalVariablesPreparer, listeners, expressionCompiler)

  private val factory = new ComponentExecutorFactory(parametersEvaluator)

  private val dynamicNodeValidator =
    new DynamicNodeValidator(
      expressionCompiler,
      globalVariablesPreparer,
      parametersEvaluator,
      definitions.globalParametersConfig
    )

  private val builtInNodeCompiler = new BuiltInNodeCompiler(expressionCompiler)

  private val fragmentParameterValidator = FragmentParameterValidator(fragmentDefinitionExtractor.classDefinitions)

  def compileSource(
      nodeData: SourceNodeData
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
  ): NodeCompilationResult[Source] = {
    import scenarioCompilationDependencies._
    implicit val nodeId: NodeId = NodeId(nodeData.id)
    nodeData match {
      case source @ Source(_, ref, _) =>
        definitions.getComponent(ComponentType.Source, ref.typ) match {
          case Some(definition) =>
            compileComponentWithContextTransformation[Source](
              nodeData = source,
              customNodeIsEndingNode = None,
              inputContext = SingleInputNodeInputValidationContext(contextWithOnlyGlobalVariables),
              componentDefinition = definition
            ).map(_._1)
          case None =>
            val error = Invalid(NonEmptyList.of(MissingSourceFactory(ref.typ)))
            // TODO: is this default behaviour ok?
            val defaultCtx =
              contextWithOnlyGlobalVariables.withVariable(
                VariableConstants.InputVariableName,
                Unknown,
                paramName = None
              )
            NodeCompilationResult(Map.empty, None, defaultCtx, error)
        }
      case frag @ FragmentInputDefinition(id, _, _) =>
        val parameterDefinitions = fragmentDefinitionExtractor.extractParametersDefinition(frag)

        val (compilationResult, displayableErrors) = definitions.getComponent(ComponentType.Fragment, id) match {
          // This case is when the fragment is stubbed with test data.
          // The validation of the fragment input is performed before the test execution starts.
          case Some(definition) =>
            val nodeCompilationResult = compileComponentWithContextTransformation[Source](
              nodeData = frag,
              customNodeIsEndingNode = None,
              inputContext = SingleInputNodeInputValidationContext(contextWithOnlyGlobalVariables),
              componentDefinition = definition
            ).map(_._1)
            (nodeCompilationResult, Valid(()))
          // For default case, we create a source that supports test with parameters
          case None =>
            val validationContext =
              staticComponentOutputValidationContextDeterminer.contextAfterFragmentInputDefinition(
                parameterDefinitions.value
              )
            val validatorsCompilationResult = parameterDefinitions.value.flatMap { paramDef =>
              paramDef.validators.map(v =>
                expressionCompiler.compileValidator(v, paramDef.name, paramDef.typ, validationContext.globalVariables)(
                  nodeId,
                  scenarioCompilationDependencies.jobData
                )
              )
            }.sequence

            val nodeCompilationResult = NodeCompilationResult(
              Map.empty,
              None,
              Valid(validationContext),
              validatorsCompilationResult.andThen(_ =>
                Valid(new FragmentSourceWithTestWithParametersSupportFactory(parameterDefinitions.value).createSource())
              )
            )

            val parameterNameValidation = fragmentParameterValidator.validateParameterNames(parameterDefinitions.value)

            // by relying on name for the field names used on FE, we display the same errors under all fields with the
            // duplicated name
            // TODO: display all errors when switching to field name errors not reliant on parameter name
            lazy val displayUniqueNameReliantErrors = parameterNameValidation.fold(
              errors => !errors.exists(_.isInstanceOf[DuplicateFragmentInputParameter]),
              _ => true
            )

            val validatedUniqueNameReliantErrorsV = nodeCompilationResult.validationContext match {
              case Valid(validationContext) if displayUniqueNameReliantErrors =>
                uniqueNameReliantErrors(frag, parameterDefinitions, validationContext)
              case _ =>
                Valid(())
            }

            val displayableErrors = parameterNameValidation |+| validatedUniqueNameReliantErrorsV
            (nodeCompilationResult, displayableErrors)
        }

        compilationResult.copy(compiledObject = displayableErrors.andThen(_ => compilationResult.compiledObject))
    }
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
        fragmentParameterValidator.validateFixedExpressionValues(
          param,
          validationContext,
          expressionCompiler.withExpressionParsers(expressionParsers =>
            expressionParsers.map {
              case (language, parser: SpelExpressionParser) =>
                language -> parser.withValidator(v => v.withTyper(t => t.withAbsentVariableReferenceAllowed(true)))
              case other => other
            }
          )
        )
      }
      .sequence
      .map(_ => ())

    val dictValueEditorErrors = fragmentInputDefinition.parameters
      .map { param =>
        fragmentParameterValidator.validateValueInputWithDictEditor(param, expressionConfig.dictionaries, classLoader)
      }
      .sequence
      .map(_ => ())

    parameterExtractionValidation |+| fixedValuesErrors |+| dictValueEditorErrors
  }

  def compileCustomNodeObject(node: SplittedNode[CustomNodeData], ctx: NodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[AnyRef] = compileCustomNodeObject(node.data, ctx, Some(node.isEnding))

  def compileCustomNodeObject(
      data: CustomNodeData,
      ctx: NodeInputValidationContext,
      customNodeIsEndingNode: Option[Boolean]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
  ): NodeCompilationResult[AnyRef] = {
    implicit val nodeId: NodeId = NodeId(data.id)

    val outputVar = data.outputVar.map(OutputVar.customNode)
    val defaultCtx = ctx match {
      case SingleInputNodeInputValidationContext(validationContext) => validationContext
      case MultipleInputBranchesNodeInputValidationContext(_, validationContextWithGlobalVariablesOnly) =>
        validationContextWithGlobalVariablesOnly
    }
    val defaultCtxToUse = outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.getComponent(ComponentType.CustomComponent, data.nodeType) match {
      case Some(componentDefinition)
          if customNodeIsEndingNode.contains(
            true
          ) && !scenarioIsAllowedToEndWithoutSink && !componentDefinition.componentTypeSpecificData.asCustomComponentData.canBeEnding =>
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(Set(nodeId.id))))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
      case Some(componentDefinition) =>
        compileComponentWithContextTransformation[AnyRef](
          nodeData = data,
          customNodeIsEndingNode = customNodeIsEndingNode,
          inputContext = ctx,
          componentDefinition = componentDefinition
        ).map(_._1)
      case None =>
        val error = Invalid(NonEmptyList.of(MissingCustomNodeExecutor(data.nodeType)))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
    }
  }

  def compileSink(
      sink: Sink,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[api.process.Sink] = {
    implicit val nodeId: NodeId = NodeId(sink.id)
    val ref                     = sink.ref

    definitions.getComponent(ComponentType.Sink, ref.typ) match {
      case Some(definition) =>
        compileComponentWithContextTransformation[api.process.Sink](
          nodeData = sink,
          customNodeIsEndingNode = None,
          inputContext = inputContext,
          componentDefinition = definition
        ).map(_._1)
      case None =>
        val error = invalid(MissingSinkFactory(sink.ref.typ)).toValidatedNel
        NodeCompilationResult(
          Map.empty[String, ExpressionTypingInfo],
          None,
          Valid(inputContext.validationContext),
          error
        )
    }
  }

  def compileFragmentInput(fragmentInput: FragmentInput, inputContext: SingleInputNodeInputValidationContext)(
      implicit jobData: JobData
  ): NodeCompilationResult[List[CompiledParameter]] = {
    implicit val nodeId: NodeId = NodeId(fragmentInput.id)

    val ref            = fragmentInput.ref
    val validParamDefs = fragmentDefinitionExtractor.extractParametersDefinition(fragmentInput)

    val childCtx = inputContext.validationContext.pushNewContext()
    val newCtx =
      validParamDefs.value.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx)) {
        case (acc, paramDef) => acc.andThen(_.withVariable(OutputVar.variable(paramDef.name.value), paramDef.typ))
      }
    val validParams =
      expressionCompiler.compileExecutorComponentNodeParameters(validParamDefs.value, ref.parameters, inputContext)
    val validParamsCombinedErrors = validParams
      .fold(Invalid(_), Valid(_), (a, _) => Invalid(a))
      .combine(
        NonEmptyList
          .fromList(validParamDefs.written)
          .map(invalid)
          .getOrElse(valid(List.empty[CompiledParameter]))
      )
    val expressionTypingInfo =
      validParams
        .map(_.map(p => p.name.value -> p.typingInfo).toMap)
        .getOrElse(Map.empty[String, ExpressionTypingInfo])
    NodeCompilationResult(expressionTypingInfo, None, newCtx, validParamsCombinedErrors)
  }

  // expression is deprecated, will be removed in the future
  def compileSwitch(
      switch: Switch,
      choices: List[(String, Expression)],
      inputContext: SingleInputNodeInputValidationContext,
  ): NodeCompilationResult[(Option[CompiledExpression], List[CompiledExpression])] = {
    implicit val nodeId: NodeId                     = NodeId(switch.id)
    val expressionRaw: Option[(String, Expression)] = Applicative[Option].product(switch.exprVal, switch.expression)
    builtInNodeCompiler.compileSwitch(expressionRaw, choices, inputContext)
  }

  def compileFilter(
      filter: Filter,
      inputContext: SingleInputNodeInputValidationContext
  ): NodeCompilationResult[CompiledExpression] = {
    implicit val nodeId: NodeId = NodeId(filter.id)
    builtInNodeCompiler.compileFilter(filter, inputContext)
  }

  def compileVariable(
      variable: Variable,
      inputContext: SingleInputNodeInputValidationContext
  ): NodeCompilationResult[CompiledExpression] = {
    implicit val nodeId: NodeId = NodeId(variable.id)
    builtInNodeCompiler.compileVariable(variable, inputContext)
  }

  def compileFragmentOutputDefinition(
      fod: FragmentOutputDefinition,
      inputContext: SingleInputNodeInputValidationContext
  ): ValidatedNel[PartSubGraphCompilationError, Map[String, TypedExpression]] = {
    implicit val nodeId: NodeId = NodeId(fod.id)
    fod.fields.map { field =>
      expressionCompiler
        .compile(field.expression, Some(ParameterName(field.name)), inputContext.validationContext, Unknown)
        .map(typedExpr => field.name -> typedExpr)
    }
  }.sequence.map(_.toMap)

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      inputContext: SingleInputNodeInputValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {
    builtInNodeCompiler.compileFields(fields, inputContext, outputVar)
  }

  def compileProcessor(
      processor: Processor,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    implicit val nodeId: NodeId = NodeId(processor.id)
    compileService(processor, inputContext)
  }

  def compileEnricher(enricher: Enricher, inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[EnricherCompilationResult] = {
    implicit val nodeId: NodeId  = NodeId(enricher.id)
    val serviceCompilationResult = compileService(enricher, inputContext)

    val expressionCompilationResult = enricher.mockExpression match {
      case Some(mockExpression) =>
        val expectedType =
          serviceCompilationResult.validationContext.map(_.localVariables(enricher.output)).getOrElse(Unknown)
        compileEnricherMockExpression(mockExpression, expectedType, inputContext.validationContext)
          .map(Some(_))
      case None => Validated.validNel(None)
    }
    serviceCompilationResult.copy(
      compiledObject = serviceCompilationResult.compiledObject.product(expressionCompilationResult).map {
        case (service, mockedExpression) =>
          EnricherCompilationResult(service, mockedExpression)
      }
    )
  }

  private def compileEnricherMockExpression(expression: Expression, expectedType: TypingResult, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, CompiledExpression] = {
    expressionCompiler
      .compile(expression, Some(MockExpressionParameterName), ctx, expectedType)
      .map(_.expression)
  }

  private def compileService(
      serviceNodeData: ServiceNodeData,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {

    definitions.getComponent(ComponentType.Service, serviceNodeData.componentId) match {
      case Some(componentDefinition) if componentDefinition.component.isInstanceOf[EagerService] =>
        compileEagerService(serviceNodeData, componentDefinition, inputContext)
      case Some(static: MethodBasedComponentDefinitionWithImplementation) =>
        ServiceCompiler.compile(serviceNodeData, static, inputContext)
      case Some(_: DynamicComponentDefinitionWithImplementation) =>
        val error = invalid(
          CustomNodeError(
            "Not supported service implementation: DynamicComponent can be mixed only with EagerService",
            None
          )
        ).toValidatedNel
        NodeCompilationResult(
          Map.empty[String, ExpressionTypingInfo],
          None,
          Valid(inputContext.validationContext),
          error
        )
      case Some(notSupportedComponentDefinition) =>
        throw new IllegalStateException(
          s"Not supported ${classOf[ComponentDefinitionWithImplementation].getName}: ${notSupportedComponentDefinition.getClass}"
        )
      case None =>
        val error = invalid(MissingService(serviceNodeData.componentId)).toValidatedNel
        NodeCompilationResult(
          Map.empty[String, ExpressionTypingInfo],
          None,
          Valid(inputContext.validationContext),
          error
        )
    }
  }

  private def compileEagerService(
      nodeData: ServiceNodeData,
      componentDefinition: ComponentDefinitionWithImplementation,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    def createService(invoker: ServiceInvoker) =
      compiledgraph.service.ServiceRef(
        id = nodeData.service.id,
        invoker = invoker,
        resultCollector = resultCollector
      )

    val compilationResult = compileComponentWithContextTransformation[ServiceInvoker](
      nodeData = nodeData,
      customNodeIsEndingNode = None,
      inputContext = inputContext,
      componentDefinition = componentDefinition
    )
    compilationResult.map { case (serviceInvoker, _) =>
      // TODO: Currently in case of object compilation failures we prefer to create "dumb" service invoker, with empty parameters list
      //       instead of return Invalid - I assume that it is probably because of errors accumulation purpose.
      //       We should clean up this compilation process by some NodeCompilationResult refactor like introduction of WriterT monad transformer
      createService(serviceInvoker)
    }
  }

  private def unwrapContextTransformation[T](value: Any): T = (value match {
    case ct: ContextTransformation => ct.implementation
    case a                         => a
  }).asInstanceOf[T]

  private def contextWithOnlyGlobalVariables(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(scenarioCompilationDependencies.jobData)

  private def compileComponentWithContextTransformation[ComponentExecutor](
      nodeData: NodeData,
      customNodeIsEndingNode: Option[Boolean],
      inputContext: NodeInputValidationContext,
      componentDefinition: ComponentDefinitionWithImplementation
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
      nodeId: NodeId
  ): NodeCompilationResult[(ComponentExecutor, List[NodeParameter])] = {
    componentDefinition match {
      case dynamicComponent: DynamicComponentDefinitionWithImplementation =>
        val nodeCompilationDependencies = createNodeCompilationDependencies(nodeData)
        val afterValidation =
          dynamicNodeValidator
            .validateNode(
              compilationDependencies = nodeCompilationDependencies,
              component = dynamicComponent.component,
              parametersConfig = dynamicComponent.parametersConfig,
              nodeInputValidationContext = inputContext
            )
            .map {
              case TransformationResult(
                    Nil,
                    computedParameters,
                    outputContext,
                    finalState,
                    nodeParameters,
                    eagerEvaluatedParamsResults
                  ) =>
                val computedParameterNames = computedParameters.filterNot(_.branchParam).map(p => p.name)
                val withoutRedundant       = nodeParameters.filter(p => computedParameterNames.contains(p.name))
                val (typingInfo, validComponentExecutor) = withCompiledParameters[ComponentExecutor](
                  parameterDefinitionsToUse = computedParameters,
                  nodeParameters = withoutRedundant,
                  nodeBranchParameters = nodeData.branchParametersOrEmpty,
                  nodeInputValidationContext = inputContext,
                  evaluatedParamsResults = eagerEvaluatedParamsResults
                ) { compiledNodeParameters =>
                  factory
                    .createComponentExecutor[ComponentExecutor](
                      new ComponentExecutorDependencies(
                        componentDefinition = componentDefinition,
                        compiledParameters = compiledNodeParameters.parameters,
                        compilationEvaluationResults = compiledNodeParameters.evaluationResults,
                        nodeCompilationDependencies = nodeCompilationDependencies,
                        nonServicesLazyParamStrategy = nonServicesLazyParamStrategy,
                        invocationContext = Some(DynamicComponentInvocationContext(FinalStateValue(finalState))),
                      )
                    )
                }
                (
                  typingInfo,
                  Some(computedParameters),
                  outputContext,
                  validComponentExecutor.map((_, withoutRedundant))
                )
              case TransformationResult(h :: t, computedParameters, outputContext, _, _, _) =>
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
        val (typingInfo, validComponentExecutor) = withCompiledParameters[ComponentExecutor](
          parameterDefinitionsToUse = staticComponent.parameters,
          nodeParameters = nodeData.parametersOrEmpty,
          nodeBranchParameters = nodeData.branchParametersOrEmpty,
          nodeInputValidationContext = inputContext,
          // static components have no earlier parameters-evaluation pass whose results could be reused
          evaluatedParamsResults = Map.empty
        ) { compiledNodeParameters =>
          factory
            .createComponentExecutor[ComponentExecutor](
              new ComponentExecutorDependencies(
                componentDefinition = componentDefinition,
                nodeCompilationDependencies = createNodeCompilationDependencies(nodeData),
                compiledParameters = compiledNodeParameters.parameters,
                compilationEvaluationResults = compiledNodeParameters.evaluationResults,
                nonServicesLazyParamStrategy = nonServicesLazyParamStrategy,
                invocationContext = None,
              )
            )
        }
        val nextCtx = staticComponentOutputValidationContextDeterminer.contextAfterNode(
          nodeData = nodeData,
          customNodeIsEndingNode = customNodeIsEndingNode,
          staticComponent = staticComponent,
          validComponentExecutor = validComponentExecutor,
          inputContext = inputContext
        )(scenarioCompilationDependencies.jobData)
        val unwrappedComponentExecutor =
          validComponentExecutor
            .map(unwrapContextTransformation[ComponentExecutor](_))
            .map((_, nodeData.parametersOrEmpty))
        NodeCompilationResult(typingInfo, Some(staticComponent.parameters), nextCtx, unwrappedComponentExecutor)
    }
  }

  private def withCompiledParameters[T](
      parameterDefinitionsToUse: List[Parameter],
      nodeParameters: List[NodeParameter],
      nodeBranchParameters: List[BranchParameters],
      nodeInputValidationContext: NodeInputValidationContext,
      evaluatedParamsResults: Map[ParameterName, EagerParameterEvaluationResult],
  )(f: CompiledNodeParameters => IorNel[ProcessCompilationError, T])(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    import scenarioCompilationDependencies._

    val compiledExecutorWithTypingInfo = for {
      compiledNodeParameters <- expressionCompiler
        .compileNodeParameters(
          parameterDefinitions = parameterDefinitionsToUse,
          nodeParameters = nodeParameters,
          nodeBranchParameters = nodeBranchParameters,
          inputContext = nodeInputValidationContext,
          evaluatedParamsResults = evaluatedParamsResults,
        )
      result <- f(compiledNodeParameters)
    } yield {
      val typingInfo = compiledNodeParameters.parameters.flatMap {
        case (TypedParameter(name, SingleBranchTypedValue(TypedExpression(_, typingInfo), _)), _) =>
          List(name.value -> typingInfo)
        case (TypedParameter(paramName, MultipleBranchesTypedValue(valueByBranch)), _) =>
          valueByBranch.map { case (branch, SingleBranchTypedValue(TypedExpression(_, typingInfo), _)) =>
            val expressionId = branchParameterExpressionId(paramName, branch)
            expressionId -> typingInfo
          }
      }.toMap
      (typingInfo, result)
    }
    (
      compiledExecutorWithTypingInfo.map(_._1).getOrElse(Map.empty),
      compiledExecutorWithTypingInfo.map(_._2).fold(Invalid(_), Valid(_), (a, _) => Invalid(a))
    )
  }

  // This class is extracted to a separate object, as handling service needs serious refactor (see comment in ServiceReturningType), and we don't want
  // methods that will probably be replaced to be mixed with others
  private object ServiceCompiler {

    def compile(
        serviceNodeData: ServiceNodeData,
        componentDefinition: MethodBasedComponentDefinitionWithImplementation,
        inputContext: SingleInputNodeInputValidationContext
    )(
        implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
        nodeId: NodeId
    ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
      import scenarioCompilationDependencies._
      val computedParameters =
        expressionCompiler.compileExecutorComponentNodeParameters(
          componentDefinition.parameters,
          serviceNodeData.parameters,
          inputContext
        )
      val outputVar                                 = serviceNodeData.outputVar.map(OutputVar.enricher)
      implicit val implicitComponentId: ComponentId = componentDefinition.id
      val outputCtx = StaticComponentOutputValidationContextDeterminer.conntextAfterService(
        inputContext,
        outputVar,
        componentDefinition.returnType
      )

      val compiledServiceRef = computedParameters.map { params =>
        val evaluateParams = { context: Context =>
          val nameToRawValueMap = params
            .map(p => p.name -> parametersEvaluator.evaluateParameterToRawValue(context, p))
            .toMap
          Params.fromRawValuesMap(nameToRawValueMap)
        }
        compiledgraph.service.ServiceRef(
          id = serviceNodeData.service.id,
          invoker = new MethodBasedServiceInvoker(
            componentDefinition,
            createNodeCompilationDependencies(serviceNodeData),
            evaluateParams
          ),
          resultCollector = resultCollector
        )
      }
      val nodeTypingInfo = computedParameters.map(_.map(p => p.name.value -> p.typingInfo).toMap).getOrElse(Map.empty)
      NodeCompilationResult(
        nodeTypingInfo,
        None,
        outputCtx,
        compiledServiceRef.fold(Invalid(_), Valid(_), (a, _) => Invalid(a))
      )
    }

  }

  private def createNodeCompilationDependencies(
      nodeData: NodeData
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies) = {
    new NodeCompilationDependencies(
      scenarioCompilationDependencies,
      nodeData,
      runtimeMode.createContext(nodesDeploymentData.get(NodeId(nodeData.id)))
    )
  }

}
