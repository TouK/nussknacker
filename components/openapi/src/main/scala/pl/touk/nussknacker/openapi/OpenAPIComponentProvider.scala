package pl.touk.nussknacker.openapi

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._
import pl.touk.nussknacker.openapi.OpenAPIsConfig._
import pl.touk.nussknacker.openapi.discovery.SwaggerOpenApiDefinitionDiscovery
import pl.touk.nussknacker.openapi.enrichers.{SwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.http.backend.{DefaultHttpClientConfig, HttpClientConfig}
import pl.touk.nussknacker.openapi.parser.ServiceParseError

import java.net.URL
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.control.NonFatal

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = {
    val discoveryUrl = config.as[URL]("url")
    val openAPIsConfig = config.rootAs[OpenAPIServicesConfig]
    val services = try {
      SwaggerOpenApiDefinitionDiscovery.discoverOpenAPIServices(discoveryUrl, openAPIsConfig)
    } catch {
      case NonFatal(ex) =>
        logger.error("OpenAPI service resolution failed. Will be used empty services lists", ex)
        List.empty
    }
    val servicesToUse = services.collect {
      case Valid(service) => ConfigFactory.parseString(service.asJson.spaces2).root()
    }
    logErrors(services)
    config.withValue("services", ConfigValueFactory.fromIterable(servicesToUse.asJava))
  }

  private def logErrors(services: List[Validated[ServiceParseError, SwaggerService]]): Unit = {
    val errors = services.collect {
      case Invalid(serviceError) => s"${serviceError.name.value} (${serviceError.errors.toList.mkString(", ")})"
    }
    if (errors.nonEmpty) {
      logger.warn(s"Failed to parse following services: ${errors.mkString(", ")}")
    }
  }

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val definitionUrl = config.as[URL]("url")
    val openAPIsConfig = config.rootAs[OpenAPIServicesConfig]
    val serviceDefinitionConfig = config.getList("services").render(ConfigRenderOptions.concise())
    val swaggerServices =
      CirceUtil.decodeJsonUnsafe[List[SwaggerService]](serviceDefinitionConfig, "Failed to parse service config")

    //TODO: configuration
    val fixedParameters: Map[String, () => AnyRef] = Map.empty
    new SwaggerEnrichers(definitionUrl, openAPIsConfig.rootUrl, prepareBaseEnricherCreator(config))
      .enrichers(swaggerServices, Nil, fixedParameters)
      .map(service => ComponentDefinition(service.name.value, service.service, docsUrl = service.documentation)).toList
  }

  protected def prepareBaseEnricherCreator(config: Config): SwaggerEnricherCreator = {
    val clientConfig = config.getAs[HttpClientConfig]("httpClientConfig").getOrElse(DefaultHttpClientConfig())
    SwaggerEnricherCreator(clientConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
