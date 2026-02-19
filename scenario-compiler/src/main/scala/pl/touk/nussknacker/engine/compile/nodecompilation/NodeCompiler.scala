package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data._
import cats.data.Validated.{invalid, valid, Invalid, Valid}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{api, compiledgraph, ModelData, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.ComponentExecutorFactory.ComponentExecutorDependencies
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.{
  CompilesTo,
  EnricherCompilationResult,
  MockExpressionParameterName,
  NodeCompilationResult
}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.compiledgraph.service.ServiceRef
import pl.touk.nussknacker.engine.compiledgraph.variable.Field
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
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.branchParameterExpressionId
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CompilableNodeData, _}
import pl.touk.nussknacker.engine.resultcollector.{
  PreventInvocationCollector,
  ProductionServiceInvocationCollector,
  ResultCollector
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.reflect.{classTag, ClassTag}

object NodeCompiler {

  val MockExpressionParameterName: ParameterName = ParameterName("$mockExpression")

  def apply(modelData: ModelData): NodeCompiler = {
    apply(
      modelData = modelData,
      resultCollector = ProductionServiceInvocationCollector,
      expressionCompiler = ExpressionCompiler.withoutOptimization(modelData)
    )
  }

  def forValidation(modelData: ModelData): NodeCompiler = {
    apply(
      modelData = modelData,
      resultCollector = PreventInvocationCollector,
      expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper
    )
  }

  private def apply(
      modelData: ModelData,
      resultCollector: ResultCollector,
      expressionCompiler: ExpressionCompiler
  ): NodeCompiler = {
    new NodeCompiler(
      definitions = modelData.modelDefinition,
      fragmentDefinitionExtractor = new FragmentParametersDefinitionExtractor(
        modelData.modelClassLoader,
        modelData.modelDefinitionWithClasses.classDefinitions,
        modelData.modelConfig.globalParametersConfig
      ),
      expressionCompiler = expressionCompiler,
      classLoader = modelData.modelClassLoader,
      listeners = Seq.empty,
      resultCollector = ProductionServiceInvocationCollector,
      runtimeMode = RuntimeMode.Live,
      nodesDeploymentData = NodesDeploymentData.empty,
      nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
    )
  }

  trait CompilesTo[NodeData <: CompilableNodeData] {
    type ReturnType
  }

  object CompilesTo {

    implicit val compilableNodeDataCompilesTo: CompilesTo[CompilableNodeData] { type ReturnType = Any } =
      new CompilesTo[CompilableNodeData] {
        override type ReturnType = Any
      }

    implicit val sourceNodeDataCompilesTo: CompilesTo[SourceNodeData] { type ReturnType = api.process.Source } =
      new CompilesTo[SourceNodeData] {
        override type ReturnType = api.process.Source
      }

    implicit val sourceCompilesTo: CompilesTo[node.Source] { type ReturnType = api.process.Source } =
      new CompilesTo[node.Source] {
        override type ReturnType = api.process.Source
      }

    implicit val sinkCompilesTo: CompilesTo[node.Sink] { type ReturnType = api.process.Sink } =
      new CompilesTo[node.Sink] {
        override type ReturnType = api.process.Sink
      }

    implicit val customNodeDataCompilesTo: CompilesTo[CustomNodeData] { type ReturnType = AnyRef } =
      new CompilesTo[CustomNodeData] {
        override type ReturnType = AnyRef
      }

    implicit val processorCompilesTo: CompilesTo[Processor] { type ReturnType = ServiceRef } =
      new CompilesTo[Processor] {
        override type ReturnType = ServiceRef
      }

    implicit val enricherCompilesTo: CompilesTo[Enricher] { type ReturnType = EnricherCompilationResult } =
      new CompilesTo[Enricher] {
        override type ReturnType = EnricherCompilationResult
      }

    implicit val filterCompilesTo: CompilesTo[Filter] { type ReturnType = CompiledExpression } =
      new CompilesTo[Filter] {
        override type ReturnType = CompiledExpression
      }

    implicit val variableCompilesTo: CompilesTo[Variable] { type ReturnType = CompiledExpression } =
      new CompilesTo[Variable] {
        override type ReturnType = CompiledExpression
      }

    implicit val variableBuilderCompilesTo: CompilesTo[VariableBuilder] { type ReturnType = List[Field] } =
      new CompilesTo[VariableBuilder] {
        override type ReturnType = List[Field]
      }

    implicit val fragmentOutputDefinitionCompilesTo
        : CompilesTo[FragmentOutputDefinition] { type ReturnType = List[Field] } =
      new CompilesTo[FragmentOutputDefinition] {
        override type ReturnType = List[Field]
      }

    implicit val switchCompilesTo
        : CompilesTo[Switch] { type ReturnType = (Option[CompiledExpression], List[CompiledExpression]) } =
      new CompilesTo[Switch] {
        override type ReturnType = (Option[CompiledExpression], List[CompiledExpression])
      }

  }

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

  private def scenarioIsAllowedToEndWithoutSink(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ) = {
    lazy val allowEndingScenarioWithoutSink = definitions.allowEndingScenarioWithoutSink
    allowEndingScenarioWithoutSink
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

  def compileNode[NodeData <: CompilableNodeData](
      nodeData: NodeData,
      variableTypes: Map[String, TypingResult] = Map.empty,
      branchVariableTypes: Option[Map[String, Map[String, TypingResult]]] = None,
      outgoingEdges: List[OutgoingEdge] = List.empty
  )(
      implicit compilesTo: CompilesTo[NodeData],
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compilesTo.ReturnType] = {
    import scenarioCompilationDependencies._

    val validationContextWithGlobalVariablesOnly =
      GlobalVariablesPreparer(definitions.expressionConfig)
        .prepareValidationContextWithGlobalVariablesOnly(jobData)

    lazy val validationContext = SingleInputNodeInputValidationContext(
      validationContextWithGlobalVariablesOnly.copy(localVariables = variableTypes)
    )
    lazy val branchCtxs = {
      val branchContexts = branchVariableTypes
        .getOrElse(Map.empty)
        .mapValuesNow { branchVariableTypes =>
          validationContextWithGlobalVariablesOnly.copy(localVariables = branchVariableTypes)
        }
      MultipleInputBranchesNodeInputValidationContext(branchContexts, validationContextWithGlobalVariablesOnly)
    }

    val compilationResult = ThreadUtils.withContextClassLoader(classLoader) {
      nodeData match {
        case a: Join =>
          compileCustomNodeObject(
            data = a,
            ctx = branchCtxs,
            customNodeIsEndingNode = None
          )
        case a: CustomNode =>
          compileCustomNodeObject(
            data = a,
            ctx = validationContext,
            customNodeIsEndingNode = None
          )
        case a: SourceNodeData => compileSource(a)
        case a: Sink           => compileSink(a, validationContext)
        case a: Enricher       => compileEnricher(a, validationContext)
        case a: Processor      => compileProcessor(a, validationContext)
        case a: Filter         => compileFilter(a, validationContext)
        case a: Variable       => compileVariable(a, validationContext)
        case a: VariableBuilder =>
          implicit val nodeId: NodeId = a.id
          compileFields(a.fields, validationContext, outputVar = Some(OutputVar.variable(a.varName)))
        case a: FragmentOutputDefinition =>
          implicit val nodeId: NodeId = a.id
          compileFields(a.fields, validationContext, outputVar = None)
        case a: Switch =>
          compileSwitch(
            a,
            outgoingEdges.collect { case OutgoingEdge(k, Some(NextSwitch(expression))) =>
              (k, expression)
            },
            validationContext
          )
      }
    }
    compilationResult.copy(compiledObject = compilationResult.compiledObject.map(_.asInstanceOf[compilesTo.ReturnType]))
  }

  private[compile] def compileSource(
      nodeData: SourceNodeData
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
  ): NodeCompilationResult[Source] = {
    import scenarioCompilationDependencies._
    implicit val nodeId: NodeId = nodeData.id
    nodeData match {
      case source @ Source(_, _, ref, _) =>
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
      case frag @ FragmentInputDefinition(id, _, _, _) =>
        val parameterDefinitions = fragmentDefinitionExtractor.extractParametersDefinition(frag)

        val (compilationResult, displayableErrors) = definitions.getComponent(ComponentType.Fragment, id.value) match {
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
                language -> parser.withTyper(t => t.withAbsentVariableReferenceAllowed(true))
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

  private[compile] def compileCustomNodeObject(node: SplittedNode[CustomNodeData], ctx: NodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[AnyRef] = compileCustomNodeObject(node.data, ctx, Some(node.isEnding))

  private[compile] def compileCustomNodeObject(
      data: CustomNodeData,
      ctx: NodeInputValidationContext,
      customNodeIsEndingNode: Option[Boolean]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies,
  ): NodeCompilationResult[AnyRef] = {
    implicit val nodeId: NodeId = data.id

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
        val error = Invalid(NonEmptyList.of(InvalidTailOfBranch(Set(nodeId))))
        NodeCompilationResult(Map.empty, None, defaultCtxToUse, error)
      case Some(componentDefinition) =>
        compileComponentWithContextTransformation[AnyRef]( // For custom nodes, we don't have a base class yet
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

  private[compile] def compileSink(
      sink: Sink,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[api.process.Sink] = {
    implicit val nodeId: NodeId = sink.id
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

  private[compile] def compileFragmentInput(
      fragmentInput: FragmentInput,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit jobData: JobData
  ): NodeCompilationResult[List[CompiledParameter]] = {
    implicit val nodeId: NodeId = fragmentInput.id

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
  private[compile] def compileSwitch(
      switch: Switch,
      choices: List[(String, Expression)],
      inputContext: SingleInputNodeInputValidationContext,
  ): NodeCompilationResult[(Option[CompiledExpression], List[CompiledExpression])] = {
    implicit val nodeId: NodeId                     = switch.id
    val expressionRaw: Option[(String, Expression)] = Applicative[Option].product(switch.exprVal, switch.expression)
    builtInNodeCompiler.compileSwitch(expressionRaw, choices, inputContext)
  }

  private[compile] def compileFilter(
      filter: Filter,
      inputContext: SingleInputNodeInputValidationContext
  ): NodeCompilationResult[CompiledExpression] = {
    implicit val nodeId: NodeId = filter.id
    builtInNodeCompiler.compileFilter(filter, inputContext)
  }

  private[compile] def compileVariable(
      variable: Variable,
      inputContext: SingleInputNodeInputValidationContext
  ): NodeCompilationResult[CompiledExpression] = {
    implicit val nodeId: NodeId = variable.id
    builtInNodeCompiler.compileVariable(variable, inputContext)
  }

  private[compile] def compileFragmentOutputDefinition(
      fod: FragmentOutputDefinition,
      inputContext: SingleInputNodeInputValidationContext
  ): ValidatedNel[PartSubGraphCompilationError, Map[String, TypedExpression]] = {
    implicit val nodeId: NodeId = fod.id
    fod.fields.map { field =>
      expressionCompiler
        .compile(field.expression, Some(ParameterName(field.name)), inputContext.validationContext, Unknown)
        .map(typedExpr => field.name -> typedExpr)
    }
  }.sequence.map(_.toMap)

  private[compile] def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      inputContext: SingleInputNodeInputValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {
    builtInNodeCompiler.compileFields(fields, inputContext, outputVar)
  }

  private[compile] def compileProcessor(
      processor: Processor,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[compiledgraph.service.ServiceRef] = {
    implicit val nodeId: NodeId = processor.id
    compileService(processor, inputContext)
  }

  private[compile] def compileEnricher(enricher: Enricher, inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): NodeCompilationResult[EnricherCompilationResult] = {
    implicit val nodeId: NodeId  = enricher.id
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
      createService(serviceInvoker)
    }
  }

  private def contextWithOnlyGlobalVariables(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(scenarioCompilationDependencies.jobData)

  private def compileComponentWithContextTransformation[ComponentExecutor: ClassTag](
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
        val nodeCompilationDependencies = createNodeCompilationDependencies(nodeData, inputContext)
        val afterValidation =
          dynamicNodeValidator
            .validateNode(
              compilationDependencies = nodeCompilationDependencies,
              component = dynamicComponent.component,
              parametersConfig = dynamicComponent.parametersConfig,
              nodeInputValidationContext = inputContext
            )
            .map {
              case TransformationResult(Nil, computedParameters, outputValidationContext, finalState, nodeParameters) =>
                val computedParameterNames = computedParameters.filterNot(_.branchParam).map(p => p.name)
                val withoutRedundant       = nodeParameters.filter(p => computedParameterNames.contains(p.name))
                val (typingInfo, validComponentExecutor) = withCompiledParameters[ComponentExecutor](
                  parameterDefinitionsToUse = computedParameters,
                  nodeParameters = withoutRedundant,
                  nodeBranchParameters = nodeData.branchParametersOrEmpty,
                  nodeInputValidationContext = inputContext
                ) { compiledParameters =>
                  factory
                    .createComponentExecutor[ComponentExecutor](
                      new ComponentExecutorDependencies(
                        componentDefinition = componentDefinition,
                        compiledParameters = compiledParameters,
                        nodeCompilationDependencies = nodeCompilationDependencies,
                        nonServicesLazyParamStrategy = nonServicesLazyParamStrategy,
                        invocationContext =
                          Some(DynamicComponentInvocationContext(FinalStateValue(finalState), outputValidationContext)),
                      )
                    )
                }
                (
                  typingInfo,
                  Some(computedParameters),
                  outputValidationContext,
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
        val (typingInfo, validComponentExecutor) = withCompiledParameters[Any](
          parameterDefinitionsToUse = staticComponent.parameters,
          nodeParameters = nodeData.parametersOrEmpty,
          nodeBranchParameters = nodeData.branchParametersOrEmpty,
          nodeInputValidationContext = inputContext
        ) { compiledParameters =>
          factory
            .createComponentExecutor[Any]( // We expect either ComponentExecutor or ContextTransformation
              new ComponentExecutorDependencies(
                componentDefinition = componentDefinition,
                nodeCompilationDependencies = createNodeCompilationDependencies(nodeData, inputContext),
                compiledParameters = compiledParameters,
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
            .map(unwrapContextTransformation[ComponentExecutor](componentDefinition, _))
            .map((_, nodeData.parametersOrEmpty))
        NodeCompilationResult(typingInfo, Some(staticComponent.parameters), nextCtx, unwrappedComponentExecutor)
    }
  }

  private def unwrapContextTransformation[ComponentExecutor: ClassTag](
      componentDefinition: ComponentDefinitionWithImplementation,
      value: Any
  ): ComponentExecutor = {
    def handleUnexpectedExecutorClass(obj: Any): Nothing = throw new ClassCastException(
      s"Object returned by [${componentDefinition.id}] component is ${Option(obj).map(ir => s"of ${ir.getClass.getName} class").orNull} but should be a ${classTag[ComponentExecutor].runtimeClass.getName}"
    )
    value match {
      case ct: AbstractContextTransformation =>
        ct.implementation match {
          case a: ComponentExecutor => a
          case other                => handleUnexpectedExecutorClass(other)
        }
      case a: ComponentExecutor => a
      case other                => handleUnexpectedExecutorClass(other)
    }
  }

  private def withCompiledParameters[T](
      parameterDefinitionsToUse: List[Parameter],
      nodeParameters: List[NodeParameter],
      nodeBranchParameters: List[BranchParameters],
      nodeInputValidationContext: NodeInputValidationContext,
  )(f: List[(TypedParameter, Parameter)] => IorNel[ProcessCompilationError, T])(
      implicit nodeId: NodeId,
      scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): (Map[String, ExpressionTypingInfo], ValidatedNel[ProcessCompilationError, T]) = {
    import scenarioCompilationDependencies._

    val compiledExecutorWithTypingInfo = for {
      compiledParameters <- expressionCompiler
        .compileNodeParameters(
          parameterDefinitions = parameterDefinitionsToUse,
          nodeParameters = nodeParameters,
          nodeBranchParameters = nodeBranchParameters,
          inputContext = nodeInputValidationContext,
        )
      result <- f(compiledParameters)
    } yield {
      val typingInfo = compiledParameters.flatMap {
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
            createNodeCompilationDependencies(serviceNodeData, inputContext),
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
      nodeData: NodeData,
      inputValidationContext: NodeInputValidationContext
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies) = {
    new NodeCompilationDependencies(
      scenarioCompilationDependencies = scenarioCompilationDependencies,
      nodeData = nodeData,
      componentUseContext = runtimeMode.createContext(nodesDeploymentData.get(nodeData.id)),
      inputValidationContext = inputValidationContext
    )
  }

}
