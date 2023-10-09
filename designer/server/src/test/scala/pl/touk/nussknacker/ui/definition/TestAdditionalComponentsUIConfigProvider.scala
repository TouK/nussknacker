package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.AdditionalComponentsUIConfigProvider.SingleComponentConfigWithoutId
import pl.touk.nussknacker.engine.api.component.{
  AdditionalComponentsUIConfigProvider,
  ComponentGroupName,
  ComponentId,
  ParameterConfig
}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesValidator}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.definition.TestAdditionalComponentsUIConfigProvider.componentGroupName

object TestAdditionalComponentsUIConfigProvider {
  val componentGroupName: ComponentGroupName = ComponentGroupName("someComponentGroup")
}

class TestAdditionalComponentsUIConfigProvider extends AdditionalComponentsUIConfigProvider {

  override def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId] = {
    if (processingType == TestProcessingTypes.Streaming) {
      Map(
        ComponentId("streaming-enricher-enricher") -> SingleComponentConfigWithoutId.zero.copy(
          params = Some(
            Map(
              "paramDualEditor" -> ParameterConfig.empty.copy(
                validators = Some(
                  List(FixedValuesValidator(possibleValues = List(FixedExpressionValue("someExpression", "someLabel"))))
                ),
              )
            )
          ),
          componentGroup = Some(componentGroupName)
        )
      )
    } else {
      Map.empty
    }
  }

}
