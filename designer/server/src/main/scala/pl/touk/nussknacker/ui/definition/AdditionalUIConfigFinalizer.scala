package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{
  AdditionalUIConfigProvider,
  ComponentId,
  ScenarioPropertyConfig,
  SingleComponentConfig
}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.component.{ComponentAdditionalConfigConverter, DefaultComponentIdProvider}

class AdditionalUIConfigFinalizer(
    additionalUIConfigProvider: AdditionalUIConfigProvider,
) {

  def finalizeModelDefinition(
      modelDefinition: ModelDefinition[ComponentStaticDefinition],
      processingType: ProcessingType
  ): ModelDefinition[ComponentStaticDefinition] = {
    val componentIdProvider = new DefaultComponentIdProvider({ (_, info) =>
      modelDefinition.getComponent(info).map(_.componentConfig)
    })

    val modelDefinitionWithIds = ModelDefinitionWithComponentIds(
      modelDefinition,
      componentIdProvider,
      processingType
    )
    val componentConfigsFromProvider = additionalUIConfigProvider
      .getAllForProcessingType(processingType)
      .mapValuesNow(ComponentAdditionalConfigConverter.toSingleComponentConfig)
    finalizeModelDefinition(modelDefinitionWithIds, componentConfigsFromProvider)
  }

  private def finalizeModelDefinition(
      modelDefinitionWithIds: ModelDefinitionWithComponentIds,
      additionalComponentsUiConfig: Map[ComponentId, SingleComponentConfig]
  ): ModelDefinition[ComponentStaticDefinition] = {
    def finalizeComponentConfig(
        idWithInfo: ComponentIdWithInfo,
        componentDefinition: ComponentStaticDefinition
    ): (String, ComponentStaticDefinition) = {
      val finalConfig = additionalComponentsUiConfig.getOrElse(
        idWithInfo.id,
        SingleComponentConfig.zero
      ) |+| componentDefinition.componentConfig

      idWithInfo.name -> componentDefinition.withComponentConfig(finalConfig)
    }

    ModelDefinition[ComponentStaticDefinition](
      modelDefinitionWithIds.components.map(finalizeComponentConfig _ tupled),
      modelDefinitionWithIds.expressionConfig,
      modelDefinitionWithIds.settings
    )
  }

  def finalizeScenarioProperties(
      scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
      processingType: ProcessingType
  ): Map[String, ScenarioPropertyConfig] = {
    additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| scenarioPropertiesConfig
  }

}
