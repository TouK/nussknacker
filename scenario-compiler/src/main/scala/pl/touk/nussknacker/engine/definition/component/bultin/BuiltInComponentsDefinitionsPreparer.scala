package pl.touk.nussknacker.engine.definition.component.bultin

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.BuiltInComponentId
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionExtractor.FinalComponentUiDefinition
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  BuiltInComponentSpecificData,
  ComponentDefinitionExtractor,
  ComponentDefinitionWithImplementation,
  ComponentStaticDefinition
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

class BuiltInComponentsDefinitionsPreparer(componentsUiConfig: ComponentsUiConfig) {

  def prepareDefinitions(forFragment: Boolean): List[ComponentDefinitionWithImplementation] = {
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
        .map { case FinalComponentUiDefinition(uiDefinition, _) =>
          // Currently built-in components are represented as method-based component, probably we should change it to some dedicated type
          MethodBasedComponentDefinitionWithImplementation.withNullImplementation(
            id.name,
            BuiltInComponentSpecificData,
            ComponentStaticDefinition(List.empty, None),
            uiDefinition,
            allowedProcessingModes =
              AllowedProcessingModes.All // built-in components are available in every processing mode
          )
        }
    }
  }

}
