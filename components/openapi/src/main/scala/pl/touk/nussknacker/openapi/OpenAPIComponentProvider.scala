package pl.touk.nussknacker.openapi

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._
import pl.touk.nussknacker.openapi.OpenAPIServicesConfig._
import pl.touk.nussknacker.openapi.discovery.{CachingOpenApiDefinitionDiscovery, SwaggerOpenApiDefinitionDiscovery}
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricherFactory

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(componentProviderConfig: Config, modelConfig: ModelConfig): List[ComponentDefinition] = {
    val openAPIsConfig = componentProviderConfig.rootAs[OpenAPIServicesConfig]
    val openApiDefinitionDiscovery =
      new CachingOpenApiDefinitionDiscovery(SwaggerOpenApiDefinitionDiscovery, openAPIsConfig)
    ComponentDefinition(
      "openAPI",
      OpenAPIEnricherFactory(openAPIsConfig, openApiDefinitionDiscovery),
      label = Some("OpenAPI")
    ) :: Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
