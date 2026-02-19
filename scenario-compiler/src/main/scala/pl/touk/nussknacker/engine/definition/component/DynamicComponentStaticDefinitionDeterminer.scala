package pl.touk.nussknacker.engine.definition.component

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.NodeName
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DynamicComponent,
  JoinDynamicComponent,
  SingleInputDynamicComponent,
  WithStaticParameters
}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext.LiveRuntime
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  DynamicNodeValidator,
  MultipleInputBranchesNodeInputValidationContext,
  NodeInputValidationContext,
  SingleInputNodeInputValidationContext
}
import pl.touk.nussknacker.engine.definition.component.DynamicComponentStaticDefinitionDeterminer.staticReturnType
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.graph.node.CustomNode
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

// This class purpose is to provide initial set of parameters that will be presented after first usage of a component.
// It is necessary to provide them, because:
// - We want to avoid flickering of parameters after first entering into the node
// - Sometimes user want to just use the component without filling parameters with own data - in this case we want to make sure
//   that parameters will be available in the scenario, even with a default values
class DynamicComponentStaticDefinitionDeterminer(
    nodeValidator: DynamicNodeValidator,
    globalVariablesPreparer: GlobalVariablesPreparer,
    globalParametersConfig: GlobalParametersConfig
) extends LazyLogging {

  private def determineStaticDefinition(
      dynamic: DynamicComponentDefinitionWithImplementation
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ComponentStaticDefinition = {
    val parameters = determineInitialParameters(dynamic)
    ComponentStaticDefinition(
      parameters,
      staticReturnType(dynamic.component)
    )
  }

  private def determineInitialParameters(
      dynamic: DynamicComponentDefinitionWithImplementation
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): List[Parameter] = {
    def inferParameters(component: DynamicComponent, inputContext: NodeInputValidationContext) = {
      // We assume that this information is not important for determining initial parameters of dynamic nodes, so we pass fake values
      val outputVariableName =
        if (dynamic.component.nodeDependencies.contains(OutputVariableNameDependency)) Some("fakeOutputVariable")
        else None
      val fakeNode = CustomNode(NodeId.generate(), NodeName("fakeNode"), outputVariableName, dynamic.name, List.empty)
      val nodeCompilationDependencies =
        new NodeCompilationDependencies(
          scenarioCompilationDependencies = scenarioCompilationDependencies,
          nodeData = fakeNode,
          componentUseContext = LiveRuntime(None),
          inputValidationContext = SingleInputNodeInputValidationContext(ValidationContext.empty)
        )
      nodeValidator
        .validateNode(
          compilationDependencies = nodeCompilationDependencies,
          component = component,
          parametersConfig = dynamic.parametersConfig,
          nodeInputValidationContext = inputContext
        )
        .map(_.parameters)
        .valueOr { err =>
          logger.warn(
            s"Errors during inferring of initial parameters for component: $component: ${err.toList.mkString(", ")}. Will be used empty list of parameters as a fallback"
          )
          // It is better to return empty list than throw an exception. User will have an option to open the node, validate node again
          // and replace those parameters by the correct once
          List.empty
        }
    }

    val globalVariablesOnlyValidationContext =
      globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(Set.empty)

    dynamic.component match {
      case withStatic: WithStaticParameters =>
        StandardParameterEnrichment.enrichParameterDefinitions(
          withStatic.staticParameters,
          dynamic.parametersConfig,
          globalParametersConfig
        )
      case single: SingleInputDynamicComponent =>
        inferParameters(single, SingleInputNodeInputValidationContext(globalVariablesOnlyValidationContext))
      case join: JoinDynamicComponent =>
        inferParameters(
          join,
          MultipleInputBranchesNodeInputValidationContext(Map.empty, globalVariablesOnlyValidationContext)
        )
    }
  }

}

object DynamicComponentStaticDefinitionDeterminer {

  def collectStaticDefinitionsForDynamicComponents(
      modelDataForType: ModelData,
      extractComponentsDefinitions: Components => List[ComponentDefinitionWithImplementation]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Map[ComponentId, ComponentStaticDefinition] = {
    val nodeValidator = DynamicNodeValidator(modelDataForType)
    val toStaticComponentDefinitionTransformer =
      new DynamicComponentStaticDefinitionDeterminer(
        nodeValidator,
        GlobalVariablesPreparer(modelDataForType.modelDefinition.expressionConfig),
        modelDataForType.modelConfig.globalParametersConfig
      )

    // We have to wrap this block with model's class loader because it invokes node compilation under the hood
    modelDataForType.withModelClassloaderAsContextClassLoader {
      extractComponentsDefinitions(modelDataForType.modelDefinition.components).collect {
        case dynamic: DynamicComponentDefinitionWithImplementation =>
          dynamic.id -> toStaticComponentDefinitionTransformer.determineStaticDefinition(dynamic)
      }.toMap
    }
  }

  def staticReturnType(component: DynamicComponent): Option[TypingResult] = {
    if (component.nodeDependencies.contains(OutputVariableNameDependency)) Some(Unknown) else None
  }

}
