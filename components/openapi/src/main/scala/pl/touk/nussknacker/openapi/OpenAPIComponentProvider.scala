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
import pl.touk.nussknacker.openapi.OpenAPIServicesConfig._
import pl.touk.nussknacker.openapi.discovery.SwaggerOpenApiDefinitionDiscovery
import pl.touk.nussknacker.openapi.enrichers.{SwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.parser.ServiceParseError

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = {
    // we need to load config to resolve url which can be potentially a system env variable
    val openAPIsConfig = ConfigFactory.load(config).rootAs[OpenAPIServicesConfig]
    val services =
      try {
        SwaggerOpenApiDefinitionDiscovery.discoverOpenAPIServices(openAPIsConfig)
      } catch {
        case NonFatal(ex) =>
          logger.error("OpenAPI service resolution failed. Will be used empty services lists", ex)
          List.empty
      }
    val servicesToUse = services.collect { case Valid(service) =>
      ConfigFactory.parseString(service.asJson.spaces2).root()
    }
    logErrors(services)
    config.withValue("services", ConfigValueFactory.fromIterable(servicesToUse.asJava))
  }

  private def logErrors(services: List[Validated[ServiceParseError, SwaggerService]]): Unit = {
    val errors = services.collect { case Invalid(serviceError) =>
      s"${serviceError.name.value} (${serviceError.errors.toList.mkString(", ")})"
    }
    if (errors.nonEmpty) {
      logger.warn(s"Failed to parse following services: ${errors.mkString(", ")}")
    }
  }

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val openAPIsConfig          = config.rootAs[OpenAPIServicesConfig]
    val serviceDefinitionConfig = config.getList("services").render(ConfigRenderOptions.concise())
    val swaggerServices =
      CirceUtil.decodeJsonUnsafe[List[SwaggerService]](serviceDefinitionConfig, "Failed to parse service config")
    val creator = prepareBaseEnricherCreator(openAPIsConfig)

    SwaggerEnrichers
      .prepare(openAPIsConfig, swaggerServices, creator)
      .map(service => ComponentDefinition(service.name.value, service.service, docsUrl = service.documentation))
      .toList
  }

  protected def prepareBaseEnricherCreator(config: OpenAPIServicesConfig): SwaggerEnricherCreator = {
    SwaggerEnricherCreator(config.httpClientConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
