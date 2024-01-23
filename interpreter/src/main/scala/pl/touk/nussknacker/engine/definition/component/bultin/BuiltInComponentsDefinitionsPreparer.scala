package pl.touk.nussknacker.engine.definition.component.bultin

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.BuiltInComponentInfo
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

  def prepareDefinitions(forFragment: Boolean): List[(String, ComponentDefinitionWithImplementation)] = {
    val componentInfos = if (forFragment) {
      BuiltInComponentInfo.AllAvailableForFragment
    } else {
      BuiltInComponentInfo.AllAvailableForScenario
    }
    componentInfos.flatMap { info =>
      val defaultConfig  = DefaultComponentConfigDeterminer.forBuiltInComponent(info)
      val combinedConfig = componentsUiConfig.getConfig(info) |+| defaultConfig
      ComponentDefinitionExtractor
        .filterOutDisabledAndComputeFinalUiDefinition(combinedConfig, componentsUiConfig.groupName)
        .map { case (uiDefinition, _) =>
          // Currently built-in components are represented as method-based component, probably we should change it to some dedicated type
          info.name -> MethodBasedComponentDefinitionWithImplementation.withNullImplementation(
            BuiltInComponentSpecificData,
            ComponentStaticDefinition(List.empty, None),
            uiDefinition
          )
        }
    }
  }

}
