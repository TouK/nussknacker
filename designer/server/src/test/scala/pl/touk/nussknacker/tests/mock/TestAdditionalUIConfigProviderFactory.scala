package pl.touk.nussknacker.tests.mock

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.tests.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
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

  val scenarioPropertyConfigOverride: Map[String, ScenarioPropertyConfig] =
    Map(
      scenarioPropertyName -> ScenarioPropertyConfig.empty.copy(
        defaultValue = Some("defaultOverride")
      )
    )

  val componentAdditionalConfigMap: Map[DesignerWideComponentId, ComponentAdditionalConfig] = Map(
    DesignerWideComponentId("streaming-service-enricher") -> ComponentAdditionalConfig(
      parameterConfigs = Map(
        "param" -> ParameterAdditionalUIConfig(
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
      componentGroup = Some(componentGroupName)
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
