package pl.touk.nussknacker.lite.manager

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, TypeSpecificInitialData}

trait LiteDeploymentManagerProvider extends DeploymentManagerProvider {

  private val streamingInitialMetData = TypeSpecificInitialData(LiteStreamMetaData(Some(1)))

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = {
    forMode(config)(
      streamingInitialMetData,
      (scenarioName: ProcessName, _: String) => RequestResponseMetaData(Some(defaultRequestResponseSlug(scenarioName, config)))
    )
  }

  protected def defaultRequestResponseSlug(scenarioName: ProcessName, config: Config): String

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = forMode(config)(
    Map.empty,
    RequestResponseOpenApiSettings.additionalPropertiesConfig
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
