package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, SingleScenarioPropertyConfig}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.api.properties.ScenarioProperties

class ScenarioPropertiesConfigFinalizer(
    additionalUIConfigProvider: AdditionalUIConfigProvider,
    processingType: ProcessingType
) {

  def finalizeScenarioPropertiesParameters(
      parametersConfig: Map[String, SingleScenarioPropertyConfig],
  ): Map[String, SingleScenarioPropertyConfig] = {
    additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| parametersConfig
  }

  def finalizePropertiesConfig(scenarioPropertiesConfig: ScenarioProperties): ScenarioProperties = {
    scenarioPropertiesConfig.copy(propertiesConfig =
      finalizeScenarioPropertiesParameters(scenarioPropertiesConfig.propertiesConfig)
    )
  }

}
