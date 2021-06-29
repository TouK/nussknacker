package pl.touk.nussknacker.openapi.enrichers

import java.net.URL
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.definition.ServiceWithExplicitMethod
import pl.touk.nussknacker.engine.api.process.{SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{OpenAPISecurityConfig, OpenAPIServicesConfig, OpenAPIsConfig, SwaggerService}

class SwaggerEnrichers(baseUrl: Option[URL]) {

  def enrichers(swaggerServices: List[SwaggerService],
                additionalCategories: List[String],
                fixedParameters: Map[String, () => AnyRef],
                clientConfig: HttpClientConfig): Seq[SwaggerEnricherDefinition] = {
    swaggerServices.map { swaggerService =>
      SwaggerEnricherDefinition(
        swaggerService.name,
        swaggerService.documentation,
        swaggerService.categories ++ additionalCategories,
        new SwaggerEnricher(baseUrl, swaggerService, fixedParameters, clientConfig)
      )
    }
  }
}

object SwaggerEnrichers {

  def enrichersForOpenAPIsConfig(openAPIsConfig: OpenAPIsConfig,
                                 predefinedCategories: List[String],
                                 fixedParameters: Map[String, () => AnyRef],
                                 clientConfig: HttpClientConfig,
                                 swaggerServiceFilter: Option[SwaggerService => Boolean]): Map[String, WithCategories[Service]] = {
    openAPIsConfig.openapis match {
      case None => Map.empty
      case Some(openapis) => openapis.flatMap {
        enrichersForOpenAPIServicesConfig(_, predefinedCategories, fixedParameters, clientConfig, swaggerServiceFilter)
      }.toMap
    }
  }

  def enrichersForOpenAPIServicesConfig(openAPIServicesConfig: OpenAPIServicesConfig,
                                        predefinedCategories: List[String],
                                        fixedParameters: Map[String, () => AnyRef],
                                        clientConfig: HttpClientConfig,
                                        swaggerServiceFilter: Option[SwaggerService => Boolean]): Map[String, WithCategories[Service]] = {
    // Warning: openapi specification can be encoded in Unicode (UTF-8, UTF-16, UTF-32)
    val definition = IOUtils.toString(openAPIServicesConfig.url, "UTF-8")
    enrichersForDefinition(definition,
      openAPIServicesConfig.rootURL,
      openAPIServicesConfig.securities.getOrElse(Map.empty),
      predefinedCategories,
      fixedParameters,
      clientConfig,
      swaggerServiceFilter)
  }

  def enrichersForDefinition(definition: String,
                             rootUrl: Option[URL],
                             securities: Map[String, OpenAPISecurityConfig],
                             predefinedCategories: List[String],
                             fixedParameters: Map[String, () => AnyRef],
                             clientConfig: HttpClientConfig,
                             swaggerServiceFilter: Option[SwaggerService => Boolean]): Map[String, WithCategories[Service]] = {
    val swaggerServices = SwaggerParser.parse(definition, securities).filter(swaggerServiceFilter.getOrElse(_ => true))
    new SwaggerEnrichers(rootUrl)
      .enrichers(swaggerServices, predefinedCategories, fixedParameters, clientConfig)
      .map { case SwaggerEnricherDefinition(name, documentation, categories, service) =>
        name -> WithCategories(
          service,
          categories,
          SingleNodeConfig.zero.copy(docsUrl = Some(documentation))
        )
      }.toMap
  }

  def enrichersForUrl(definitionUrl: URL,
                      rootUrl: Option[URL],
                      securities: Map[String, OpenAPISecurityConfig],
                      predefinedCategories: List[String],
                      fixedParameters: Map[String, () => AnyRef],
                      clientConfig: HttpClientConfig,
                      swaggerServiceFilter: Option[SwaggerService => Boolean]): Map[String, WithCategories[Service]] = {
    val openAPIServicesConfig = OpenAPIServicesConfig(definitionUrl, rootUrl, Some(securities))
    enrichersForOpenAPIServicesConfig(openAPIServicesConfig, predefinedCategories, fixedParameters, clientConfig, swaggerServiceFilter)
  }
}

final case class SwaggerEnricherDefinition(name: String, documentation: String, categories: List[String], service: ServiceWithExplicitMethod)

