package pl.touk.nussknacker.openapi

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._
import pl.touk.nussknacker.openapi.discovery.{CachingOpenApiDefinitionDiscovery, SwaggerOpenApiDefinitionDiscovery}
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricherFactory

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = {
    val openAPIsConfig = OpenAPIServicesConfig.parse(componentProviderConfig)
    val openApiDefinitionDiscovery =
      new CachingOpenApiDefinitionDiscovery(SwaggerOpenApiDefinitionDiscovery, openAPIsConfig)
    ComponentDefinition(
      name = "openAPI",
      component = OpenAPIEnricherFactory(openAPIsConfig, openApiDefinitionDiscovery),
      label = Some(s"${openAPIsConfig.componentPrefix.getOrElse("")}OpenAPI")
    ) :: Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
