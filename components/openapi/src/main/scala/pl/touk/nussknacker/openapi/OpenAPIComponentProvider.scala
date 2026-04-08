package pl.touk.nussknacker.openapi

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.discovery.{CachingOpenApiDefinitionDiscovery, SwaggerOpenApiDefinitionDiscovery}
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricherFactory
import pl.touk.nussknacker.openapi.http.backend.HttpClientProvider

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = {
    val openAPIsConfig = OpenAPIServicesConfig.parse(componentProviderConfig)
    implicit val backendProvider: HttpBackendProvider =
      HttpClientProvider.getBackendProvider(openAPIsConfig.httpClientConfig)
    val openApiDefinitionDiscovery = new CachingOpenApiDefinitionDiscovery(
      new SwaggerOpenApiDefinitionDiscovery(),
      openAPIsConfig.discoveryCacheTtl,
      openAPIsConfig.cacheFileLocation
    )
    ComponentDefinition(
      name = "openAPI",
      component = new OpenAPIEnricherFactory(openAPIsConfig, backendProvider, openApiDefinitionDiscovery),
      label = Some(s"${openAPIsConfig.componentPrefix.getOrElse("")}OpenAPI")
    ) :: Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
