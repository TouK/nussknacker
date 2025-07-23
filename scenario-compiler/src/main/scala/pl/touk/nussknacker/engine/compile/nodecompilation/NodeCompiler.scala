package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data.{NonEmptyList, Validated, ValidatedNel, Writer}
import cats.data.Validated.{invalid, valid, Invalid, Valid}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{api, compiledgraph, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonize.MissingSinkHandler
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.{
  EnricherCompilationResult,
  MockExpressionParameterName,
  NodeCompilationResult
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
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.Typeable
import shapeless.syntax.typeable._

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

  private lazy val globalVariablesPreparer          = GlobalVariablesPreparer(expressionConfig)
  private implicit val typeableJoin: Typeable[Join] = Typeable.simpleTypeable(classOf[Join])
  private val expressionConfig: ExpressionConfigDefinition =
    definitions.expressionConfig

  private val parametersEvaluator =
    new ParameterEvaluator(globalVariablesPreparer, listeners)
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
    implicit val nodeId: NodeId = NodeId(nodeData.id)
    import scenarioCompilationDependencies._
    nodeData match {
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
              SingleInputNodeInputValidationContext(contextWithOnlyGlobalVariables),
              Some(VariableConstants.InputVariableName),
              definition,
              defaultCtxForMethodBasedCreatedComponentExecutor
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
        val parameterDefinitions                 = fragmentDefinitionExtractor.extractParametersDefinition(frag)
        val variables: Map[String, TypingResult] = parameterDefinitions.value.map(a => a.name.value -> a.typ).toMap
        val validationContext                    = contextWithOnlyGlobalVariables.copy(localVariables = variables)

        val compilationResult = definitions.getComponent(ComponentType.Fragment, id) match {
          // This case is when fragment is stubbed with test data
          case Some(definition) =>
            compileComponentWithContextTransformation[Source](
              Nil,
              Nil,
              SingleInputNodeInputValidationContext(contextWithOnlyGlobalVariables),
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

        val parameterNameValidation = fragmentParameterValidator.validateParameterNames(parameterDefinitions.value)

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

  def compileCustomNodeObject(data: CustomNodeData, ctx: NodeInputValidationContext, ending: Boolean)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
  ): NodeCompilationResult[AnyRef] = {
    implicit val nodeId: NodeId = NodeId(data.id)
    import scenarioCompilationDependencies._

    val outputVar = data.outputVar.map(OutputVar.customNode)
    val defaultCtx = ctx match {
      case SingleInputNodeInputValidationContext(validationContext) => validationContext
      case MultipleInputBranchesNodeInputValidationContext(_, validationContextWithGlobalVariablesOnly) =>
        validationContextWithGlobalVariablesOnly
    }
    val defaultCtxToUse = outputVar.map(defaultCtx.withVariable(_, Unknown)).getOrElse(Valid(defaultCtx))

    definitions.getComponent(ComponentType.CustomComponent, data.nodeType) match {
      case Some(componentDefinition)
          if ending && !scenarioIsAllowedToEndWithoutSink && !componentDefinition.componentTypeSpecificData.asCustomComponentData.canBeEnding =>
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
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[api.process.Sink] = {
    implicit val nodeId: NodeId = NodeId(sink.id)
    val ref                     = sink.ref

    definitions.getComponent(ComponentType.Sink, ref.typ) match {
      case Some(definition) =>
        compileComponentWithContextTransformation[api.process.Sink](
          sink.parameters,
          Nil,
          inputContext,
          None,
          definition,
          _ => Valid(inputContext.validationContext)
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
      n: Processor,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    implicit val nodeId: NodeId = NodeId(n.id)
    compileService(n.service, inputContext, None)
  }

  def compileEnricher(n: Enricher, inputContext: SingleInputNodeInputValidationContext, outputVar: OutputVar)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[EnricherCompilationResult] = {
    implicit val nodeId: NodeId  = NodeId(n.id)
    val serviceCompilationResult = compileService(n.service, inputContext, Some(outputVar))

    val expressionCompilationResult = n.mockExpression match {
      case Some(mockExpression) =>
        val expectedType =
          serviceCompilationResult.validationContext.map(_.localVariables(outputVar.outputName)).getOrElse(Unknown)
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
      n: ServiceRef,
      inputContext: SingleInputNodeInputValidationContext,
      outputVar: Option[OutputVar]
  )(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    import scenarioCompilationDependencies._

    definitions.getComponent(ComponentType.Service, n.id) match {
      case Some(componentDefinition) if componentDefinition.component.isInstanceOf[EagerService] =>
        compileEagerService(n, componentDefinition, inputContext, outputVar)
      case Some(static: MethodBasedComponentDefinitionWithImplementation) =>
        ServiceCompiler.compile(n, outputVar, static, inputContext)
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
        val error = invalid(MissingService(n.id)).toValidatedNel
        NodeCompilationResult(
          Map.empty[String, ExpressionTypingInfo],
          None,
          Valid(inputContext.validationContext),
          error
        )
    }
  }

  private def compileEagerService(
      serviceRef: ServiceRef,
      componentDefinition: ComponentDefinitionWithImplementation,
      inputContext: SingleInputNodeInputValidationContext,
      outputVar: Option[OutputVar]
  )(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    val defaultCtxForMethodBasedCreatedComponentExecutor
        : Option[TypingResult] => ValidatedNel[ProcessCompilationError, ValidationContext] = returnTypeOpt =>
      outputVar match {
        case Some(out) =>
          returnTypeOpt
            .map(inputContext.validationContext.withVariable(out, _))
            .getOrElse {
              logger.warn(
                s"Scenario [${scenarioCompilationDependencies.metaData.name}] node [$nodeId] compilation warning. " +
                  s"Found ${out.fieldName} = ${out.outputName} but service [${serviceRef.id}] used by the node doesn't need it. It will be skipped."
              )
              Valid(inputContext.validationContext)
            }
        case None => Valid(inputContext.validationContext)
      }

    def createService(invoker: ServiceInvoker) =
      compiledgraph.service.ServiceRef(
        id = serviceRef.id,
        invoker = invoker,
        resultCollector = resultCollector
      )

    val compilationResult = compileComponentWithContextTransformation[ServiceInvoker](
      parameters = serviceRef.parameters,
      branchParameters = Nil,
      inputContext = inputContext,
      outputVar = outputVar.map(_.outputName),
      componentDefinition = componentDefinition,
      defaultCtxForMethodBasedCreatedComponentExecutor = defaultCtxForMethodBasedCreatedComponentExecutor
    )
    compilationResult.map { case (serviceInvoker, nodeParams) =>
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

  private def contextWithOnlyGlobalVariables(implicit jobData: JobData): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)

  private def defaultContextAfter(
      node: CustomNodeData,
      ending: Boolean,
      branchCtx: NodeInputValidationContext
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Option[TypingResult] => ValidatedNel[ProcessCompilationError, ValidationContext] =
    returnTypeOpt => {
      val validationContext = branchCtx match {
        case SingleInputNodeInputValidationContext(validationContext) => validationContext
        case MultipleInputBranchesNodeInputValidationContext(_, validationContextWithGlobalVariablesOnly) =>
          validationContextWithGlobalVariablesOnly
      }

      def ctxWithVar(outputVar: OutputVar, typ: TypingResult) = validationContext
        .withVariable(outputVar, typ)
        // ble... NonEmptyList is invariant...
        .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

      (node.outputVar, returnTypeOpt) match {
        case (Some(varName), Some(typ)) => ctxWithVar(OutputVar.customNode(varName), typ)
        case (None, None)               => Valid(validationContext)
        case (Some(outputVarValue), None) =>
          logger.warn(
            s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
              s"Found outputVar = ${outputVarValue} but custom node [${node.id}] used by the node doesn't need it. It will be skipped."
          )
          Valid(validationContext)
        case (None, Some(_)) if ending => Valid(validationContext)
        case (None, Some(_)) => Invalid(NonEmptyList.of(MissingParameters(Set(ParameterName("OutputVariable")))))
      }
    }

  private def compileComponentWithContextTransformation[ComponentExecutor](
      parameters: List[NodeParameter],
      branchParameters: List[BranchParameters],
      inputContext: NodeInputValidationContext,
      outputVar: Option[String],
      componentDefinition: ComponentDefinitionWithImplementation,
      defaultCtxForMethodBasedCreatedComponentExecutor: Option[TypingResult] => ValidatedNel[
        ProcessCompilationError,
        ValidationContext
      ]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
      nodeId: NodeId
  ): NodeCompilationResult[(ComponentExecutor, List[NodeParameter])] = {
    import scenarioCompilationDependencies._
    componentDefinition match {
      case dynamicComponent: DynamicComponentDefinitionWithImplementation =>
        val afterValidation =
          dynamicNodeValidator
            .validateNode(
              dynamicComponent.component,
              parameters,
              branchParameters,
              outputVar,
              dynamicComponent.parametersConfig,
              inputContext
            )
            .map {
              case TransformationResult(Nil, computedParameters, outputContext, finalState, nodeParameters) =>
                val computedParameterNames = computedParameters.filterNot(_.branchParam).map(p => p.name)
                val withoutRedundant       = nodeParameters.filter(p => computedParameterNames.contains(p.name))
                val (typingInfo, validComponentExecutor) = createComponentExecutor[ComponentExecutor](
                  componentDefinition,
                  withoutRedundant,
                  branchParameters,
                  outputVar,
                  inputContext,
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
          inputContext,
          staticComponent.parameters,
          Seq.empty
        )
        val nextCtx = validComponentExecutor.fold(
          _ => defaultCtxForMethodBasedCreatedComponentExecutor(staticComponent.returnType),
          executor =>
            contextAfterMethodBasedCreatedComponentExecutor(
              executor,
              inputContext,
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
      nodeInputValidationContext: NodeInputValidationContext,
      parameterDefinitionsToUse: List[Parameter],
      additionalDependencies: Seq[AnyRef]
  )(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, ComponentExecutor]) = {
    import scenarioCompilationDependencies._

    val compiledObjectWithTypingInfo = expressionCompiler
      .compileNodeParameters(
        parameterDefinitionsToUse,
        nodeParameters,
        nodeBranchParameters,
        nodeInputValidationContext,
      )
      .flatMap { compiledParameters =>
        factory
          .createComponentExecutor[ComponentExecutor](
            component = componentDefinition,
            compiledParameters = compiledParameters,
            outputVariableNameOpt = outputVariableNameOpt,
            additionalDependencies = additionalDependencies,
            componentUseContext = runtimeMode.createContext(nodesDeploymentData.get(nodeId)),
            nonServicesLazyParamStrategy = nonServicesLazyParamStrategy
          )
          .map { componentExecutor =>
            val typingInfo = compiledParameters.flatMap {
              case (TypedParameter(name, SingleBranchTypedValue(TypedExpression(_, typingInfo), _)), _) =>
                List(name.value -> typingInfo)
              case (TypedParameter(paramName, MultipleBranchesTypedValue(valueByBranch)), _) =>
                valueByBranch.map { case (branch, SingleBranchTypedValue(TypedExpression(_, typingInfo), _)) =>
                  val expressionId = branchParameterExpressionId(paramName, branch)
                  expressionId -> typingInfo
                }
            }.toMap
            (typingInfo, componentExecutor)
          }
      }
    (
      compiledObjectWithTypingInfo.map(_._1).getOrElse(Map.empty),
      compiledObjectWithTypingInfo.map(_._2).fold(Invalid(_), Valid(_), (a, _) => Invalid(a))
    )
  }

  private def contextAfterMethodBasedCreatedComponentExecutor[ComponentExecutor](
      executor: ComponentExecutor,
      validationContexts: NodeInputValidationContext,
      handleNonContextTransformingExecutor: ComponentExecutor => ValidatedNel[
        ProcessCompilationError,
        ValidationContext
      ]
  )(implicit nodeId: NodeId, jobData: JobData): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val contextTransformationDefOpt = executor.cast[AbstractContextTransformation].map(_.definition)
      (contextTransformationDefOpt, validationContexts) match {
        case (
              Some(transformation: ContextTransformationDef),
              SingleInputNodeInputValidationContext(validationContext)
            ) =>
          // copying global variables because custom transformation may override them -> TODO: in ValidationContext
          transformation.transform(validationContext).map(_.copy(globalVariables = validationContext.globalVariables))
        case (
              Some(transformation: JoinContextTransformationDef),
              MultipleInputBranchesNodeInputValidationContext(branchEndContexts, _)
            ) =>
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
    }(nodeId, jobData.metaData)
  }

  // This class is extracted to separate object, as handling service needs serious refactor (see comment in ServiceReturningType), and we don't want
  // methods that will probably be replaced to be mixed with others
  object ServiceCompiler {

    def compile(
        serviceRef: ServiceRef,
        outputVar: Option[OutputVar],
        objWithMethod: MethodBasedComponentDefinitionWithImplementation,
        inputContext: SingleInputNodeInputValidationContext
    )(implicit jobData: JobData, nodeId: NodeId): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
      val computedParameters =
        expressionCompiler.compileExecutorComponentNodeParameters(
          objWithMethod.parameters,
          serviceRef.parameters,
          inputContext
        )
      val outputCtx = outputVar match {
        case Some(output) =>
          objWithMethod.returnType
            .map(inputContext.validationContext.withVariable(output, _))
            .getOrElse {
              logger.warn(
                s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
                  s"Found ${output.fieldName} = ${output.outputName} but service [${serviceRef.id}] used by the node doesn't need it. It will be skipped."
              )
              Valid(inputContext.validationContext)
            }
        case None => Valid(inputContext.validationContext)
      }

      val compiledServiceRef = computedParameters.map { params =>
        val evaluateParams = { context: Context =>
          val nameToRawValueMap = params
            .map(p => p.name -> parametersEvaluator.evaluateParameterToRawValue(context, p))
            .toMap
          Params.fromRawValuesMap(nameToRawValueMap)
        }
        compiledgraph.service.ServiceRef(
          id = serviceRef.id,
          invoker = new MethodBasedServiceInvoker(jobData.metaData, nodeId, outputVar, objWithMethod, evaluateParams),
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

}
