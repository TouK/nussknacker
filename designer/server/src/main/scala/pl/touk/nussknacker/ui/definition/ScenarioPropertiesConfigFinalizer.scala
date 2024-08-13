package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, ScenarioPropertiesParameterConfig}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.api.properties.ScenarioPropertiesConfig

class ScenarioPropertiesConfigFinalizer(
    additionalUIConfigProvider: AdditionalUIConfigProvider,
    processingType: ProcessingType
) {

  def finalizeScenarioPropertiesParameters(
      parametersConfig: Map[String, ScenarioPropertiesParameterConfig],
  ): Map[String, ScenarioPropertiesParameterConfig] = {
    additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| parametersConfig
  }

  def finalizePropertiesConfig(scenarioPropertiesConfig: ScenarioPropertiesConfig): ScenarioPropertiesConfig = {
    scenarioPropertiesConfig.copy(parameterConfig =
      finalizeScenarioPropertiesParameters(scenarioPropertiesConfig.parameterConfig)
    )
  }

}
