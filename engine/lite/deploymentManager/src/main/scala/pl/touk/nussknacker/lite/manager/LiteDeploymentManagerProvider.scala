package pl.touk.nussknacker.lite.manager

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{LiteralIntegerValidator, MinimalNumberValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, MetaDataInitializer}

trait LiteDeploymentManagerProvider extends DeploymentManagerProvider {

  override def metaDataInitializer(config: Config): MetaDataInitializer = {
    forMode(config)(
      LitePropertiesConfig.streamingInitialMetaData,
      MetaDataInitializer("RequestResponseMetaData", scenarioName => Map("slug" -> defaultRequestResponseSlug(scenarioName, config)))
    )
  }

  protected def defaultRequestResponseSlug(scenarioName: ProcessName, config: Config): String

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = forMode(config)(
    LitePropertiesConfig.streamProperties,
    LitePropertiesConfig.requestResponseProperties
  )

  // TODO: Lite DM will be able to handle both streaming and rr, without mode, when we add scenarioType to
  //       TypeSpecificInitialData.forScenario and add scenarioType -> mode mapping with reasonable defaults to configuration
  protected def forMode[T](config: Config)(streaming: => T, requestResponse: => T): T = {
    config.getString("mode") match {
      case "streaming" => streaming
      case "request-response" => requestResponse
      case other => throw new IllegalArgumentException(s"Unsupported mode: $other")
    }
  }

}

object LitePropertiesConfig {
  val streamingInitialMetaData: MetaDataInitializer = MetaDataInitializer("LiteStreamMetaData", Map("parallelism" -> "1"))

  private val parallelismConfig: (String, AdditionalPropertyConfig) = "parallelism" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = Some(List(LiteralIntegerValidator, MinimalNumberValidator(1))),
      label = Some("Parallelism")
    )

  private val slugConfig: (String, AdditionalPropertyConfig) = "slug" ->
    AdditionalPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      validators = None,
      label = Some("Slug")
    )

  val streamProperties: Map[String, AdditionalPropertyConfig] = Map(parallelismConfig)

  val requestResponseProperties: Map[String, AdditionalPropertyConfig] =
    RequestResponseOpenApiSettings.additionalPropertiesConfig ++ Map(slugConfig)

}
