package pl.touk.nussknacker.engine.definition.component.bultin

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.BuiltInComponentInfo
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{BuiltInComponentSpecificData, ComponentStaticDefinition}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig

class BuiltInComponentsStaticDefinitionsPreparer(componentsUiConfig: ComponentsUiConfig) {

  def prepareStaticDefinitions(forFragment: Boolean): List[(String, ComponentStaticDefinition)] = {
    val componentInfos = if (forFragment) {
      BuiltInComponentInfo.AllAvailableForFragment
    } else {
      BuiltInComponentInfo.AllAvailableForScenario
    }
    componentInfos.flatMap { info =>
      val componentConfig = componentsUiConfig.getConfigByComponentName(info.name)
      if (componentConfig.disabled) {
        None
      } else {
        Some(
          info.name -> ComponentStaticDefinition(
            parameters = List.empty,
            returnType = None,
            categories = None,
            componentConfig = componentConfig |+| DefaultComponentConfigDeterminer.forBuiltInComponent(info),
            componentTypeSpecificData = BuiltInComponentSpecificData
          )
        )
      }
    }
  }

}
