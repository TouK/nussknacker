package pl.touk.nussknacker.lite.manager

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig
import pl.touk.nussknacker.engine.api.definition.{LiteralIntegerValidator, MinimalNumberValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.properties.ScenarioPropertiesConfig
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, MetaDataInitializer}

trait LiteDeploymentManagerProvider extends DeploymentManagerProvider {

  override def metaDataInitializer(config: Config): MetaDataInitializer = {
    forMode(config)(
      MetaDataInitializer(LiteStreamMetaData.typeName, Map(LiteStreamMetaData.parallelismName -> "1")),
      MetaDataInitializer(
        RequestResponseMetaData.typeName,
        scenarioName => Map(RequestResponseMetaData.slugName -> defaultRequestResponseSlug(scenarioName, config))
      )
    )
  }

  protected def defaultRequestResponseSlug(scenarioName: ProcessName, config: Config): String

  override def scenarioPropertiesConfig(config: Config): ScenarioPropertiesConfig= ScenarioPropertiesConfig.fromParameterMap(forMode(config)(
    LitePropertiesConfig.streamProperties,
    LitePropertiesConfig.requestResponseProperties
  ))

  // TODO: Lite DM will be able to handle both streaming and rr, without mode, when we add scenarioType to
  //       TypeSpecificInitialData.forScenario and add scenarioType -> mode mapping with reasonable defaults to configuration
  protected def forMode[T](config: Config)(streaming: => T, requestResponse: => T): T = {
    config.getString("mode") match {
      case "streaming"        => streaming
      case "request-response" => requestResponse
      case other              => throw new IllegalArgumentException(s"Unsupported mode: $other")
    }
  }

}

object LitePropertiesConfig {

  private val parallelismConfig: (String, ScenarioPropertiesParameterConfig) = LiteStreamMetaData.parallelismName ->
    ScenarioPropertiesParameterConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism"),
      hintText = None
    )

  private val slugConfig: (String, ScenarioPropertiesParameterConfig) = RequestResponseMetaData.slugName ->
    ScenarioPropertiesParameterConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = None,
      label = Some("Slug"),
      hintText = None
    )

  val streamProperties: Map[String, ScenarioPropertiesParameterConfig] = Map(parallelismConfig)

  val requestResponseProperties: Map[String, ScenarioPropertiesParameterConfig] =
    RequestResponseOpenApiSettings.scenarioPropertiesConfig ++ Map(slugConfig)

}
