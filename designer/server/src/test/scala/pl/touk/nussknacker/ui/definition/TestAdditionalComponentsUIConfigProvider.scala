package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.{AdditionalComponentsUIConfigProvider, ComponentId, ParameterConfig, SingleComponentUIConfig}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesValidator}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes

class TestAdditionalComponentsUIConfigProvider extends AdditionalComponentsUIConfigProvider {
  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentUIConfig] = {
    if (processingType == TestProcessingTypes.Streaming) {
      Map(
        ComponentId("streaming-enricher-enricher") -> SingleComponentUIConfig.zero.copy(
          params = Some(Map(
            "paramDualEditor" -> ParameterConfig.empty.copy(
              validators = Some(List(FixedValuesValidator(possibleValues = List(FixedExpressionValue("someExpression", "someLabel")))))
            )
          ))
        )
      )
    } else {
      Map.empty
    }
  }
}
