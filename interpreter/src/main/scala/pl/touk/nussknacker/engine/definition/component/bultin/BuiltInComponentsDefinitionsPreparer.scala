package pl.touk.nussknacker.engine.definition.component.bultin

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.BuiltInComponentId
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.{
  BuiltInComponentSpecificData,
  ComponentDefinitionExtractor,
  ComponentDefinitionWithLogic,
  ComponentStaticDefinition
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

class BuiltInComponentsDefinitionsPreparer(componentsUiConfig: ComponentsUiConfig) {

  def prepareDefinitions(forFragment: Boolean): List[ComponentDefinitionWithLogic] = {
    val componentIds = if (forFragment) {
      BuiltInComponentId.AllAvailableForFragment
    } else {
      BuiltInComponentId.AllAvailableForScenario
    }
    componentIds.flatMap { id =>
      val defaultConfig  = DefaultComponentConfigDeterminer.forBuiltInComponent(id)
      val combinedConfig = componentsUiConfig.getConfig(id) |+| defaultConfig
      ComponentDefinitionExtractor
        .filterOutDisabledAndComputeFinalUiDefinition(combinedConfig, componentsUiConfig.groupName)
        .map { case (uiDefinition, _) =>
          // Currently built-in components are represented as method-based component, probably we should change it to some dedicated type
          MethodBasedComponentDefinitionWithLogic.withNullLogic(
            id.name,
            BuiltInComponentSpecificData,
            ComponentStaticDefinition(List.empty, None),
            uiDefinition,
            allowedProcessingModes = None // built-in components are available in every processing mode
          )
        }
    }
  }

}
