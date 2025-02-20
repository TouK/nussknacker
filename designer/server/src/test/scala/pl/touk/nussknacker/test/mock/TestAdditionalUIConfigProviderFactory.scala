package pl.touk.nussknacker.test.mock

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesValidator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class TestAdditionalUIConfigProviderFactory extends AdditionalUIConfigProviderFactory {

  override def create(config: Config, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): AdditionalUIConfigProvider = TestAdditionalUIConfigProvider

}

object TestAdditionalUIConfigProvider extends AdditionalUIConfigProvider {
  val componentGroupName: ComponentGroupName = ComponentGroupName("someComponentGroup")
  val scenarioPropertyName                   = "someScenarioProperty1"

  val scenarioPropertyConfigDefault: Map[String, ScenarioPropertyConfig] =
    Map(
      scenarioPropertyName -> ScenarioPropertyConfig.empty.copy(
        defaultValue = Some("someDefault")
      )
    )

  val scenarioPropertyPossibleValues: List[FixedExpressionValue] =
    List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))

  val scenarioPropertyConfigOverride: Map[String, ScenarioPropertyConfig] =
    Map(
      scenarioPropertyName -> ScenarioPropertyConfig.empty.copy(
        defaultValue = Some("defaultValueOverride"),
        validators = Some(List(FixedValuesValidator(scenarioPropertyPossibleValues))),
        label = Some("labelOverride"),
      )
    )

  val componentAdditionalConfigMap: Map[DesignerWideComponentId, ComponentAdditionalConfig] = Map(
    DesignerWideComponentId("streaming-service-enricher") -> ComponentAdditionalConfig(
      parameterConfigs = Map(
        ParameterName("param") -> ParameterAdditionalUIConfig(
          required = true,
          initialValue = Some(
            FixedExpressionValue(
              "'default-from-additional-ui-config-provider'",
              "default-from-additional-ui-config-provider"
            )
          ),
          hintText = Some("hint-text-from-additional-ui-config-provider"),
          valueEditor = None,
          valueCompileTimeValidation = None
        )
      ),
      componentGroup = Some(componentGroupName),
      label = "enricher"
    )
  )

  override def getAllForProcessingType(
      processingType: String
  ): Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    if (processingType == Streaming.stringify)
      componentAdditionalConfigMap
    else
      Map.empty

  override def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig] =
    if (processingType == Streaming.stringify)
      scenarioPropertyConfigOverride
    else
      Map.empty

}
