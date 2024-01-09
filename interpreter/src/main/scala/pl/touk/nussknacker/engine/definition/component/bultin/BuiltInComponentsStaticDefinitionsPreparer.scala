package pl.touk.nussknacker.engine.definition.component.bultin

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.BuiltInComponentInfo
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{
  BuiltInComponentSpecificData,
  ComponentDefinitionExtractor,
  ComponentStaticDefinition,
  ConfigWithOriginalGroupName
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

class BuiltInComponentsStaticDefinitionsPreparer(componentsUiConfig: ComponentsUiConfig) {

  def prepareStaticDefinitions(forFragment: Boolean): List[(String, ComponentStaticDefinition)] = {
    val componentInfos = if (forFragment) {
      BuiltInComponentInfo.AllAvailableForFragment
    } else {
      BuiltInComponentInfo.AllAvailableForScenario
    }
    componentInfos.flatMap { info =>
      val defaultConfig  = DefaultComponentConfigDeterminer.forBuiltInComponent(info)
      val combinedConfig = componentsUiConfig.getConfig(info) |+| defaultConfig
      ComponentDefinitionExtractor.translateGroupNameAndFilterOutDisabled(combinedConfig, componentsUiConfig).map {
        case ConfigWithOriginalGroupName(finalConfig, originalGroupName) =>
          info.name -> ComponentStaticDefinition(
            parameters = List.empty,
            returnType = None,
            categories = None,
            componentConfig = finalConfig,
            originalGroupName = originalGroupName,
            componentTypeSpecificData = BuiltInComponentSpecificData
          )
      }
    }
  }

}
