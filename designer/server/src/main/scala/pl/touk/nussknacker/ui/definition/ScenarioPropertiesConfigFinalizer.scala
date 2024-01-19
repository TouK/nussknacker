package pl.touk.nussknacker.ui.definition

import cats.implicits.catsSyntaxSemigroup
import pl.touk.nussknacker.engine.api.component.{AdditionalUIConfigProvider, ScenarioPropertyConfig}
import pl.touk.nussknacker.engine.api.process.ProcessingType

class ScenarioPropertiesConfigFinalizer(
    additionalUIConfigProvider: AdditionalUIConfigProvider,
    processingType: ProcessingType
) {

  def finalizeScenarioProperties(
      scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
  ): Map[String, ScenarioPropertyConfig] = {
    additionalUIConfigProvider.getScenarioPropertiesUIConfigs(processingType) |+| scenarioPropertiesConfig
  }

}
