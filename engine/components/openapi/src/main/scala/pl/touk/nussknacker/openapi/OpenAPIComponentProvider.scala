package pl.touk.nussknacker.openapi

import com.typesafe.config.{Config, ConfigFactory, ConfigList, ConfigRenderOptions, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import net.ceedubs.ficus.Ficus._
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._
import pl.touk.nussknacker.openapi.OpenAPIsConfig._
import pl.touk.nussknacker.openapi.enrichers.{BaseSwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.http.backend.{DefaultHttpClientConfig, HttpClientConfig}
import pl.touk.nussknacker.openapi.parser.SwaggerParser

import java.net.URL
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.control.NonFatal

class OpenAPIComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "openAPI"

  override def resolveConfigForExecution(config: Config): Config = {
    // we need to use resolved config for service discovery because can be used environment variables e.g. for url
    val resolvedConfig = config.resolve()
    val openAPIsConfig = resolvedConfig.rootAs[OpenAPIServicesConfig]
    val serviceConfigs = try {
      discoverOpenAPIServices(resolvedConfig, openAPIsConfig)
    } catch {
      case NonFatal(ex) =>
        logger.error("OpenAPI service resolution failed. Will be used empty services lists", ex)
        List.empty
    }
    config.withValue("services", ConfigValueFactory.fromIterable(serviceConfigs.asJava))
  }

  private def discoverOpenAPIServices(config: Config, openAPIsConfig: OpenAPIServicesConfig) = {
    // Warning: openapi specification can be encoded in Unicode (UTF-8, UTF-16, UTF-32)
    val definition = IOUtils.toString(config.as[URL]("url"), StandardCharsets.UTF_8)
    val services = SwaggerParser.parse(definition, openAPIsConfig)

    logger.info(s"Discovered OpenAPI: ${services.map(_.name)}")

    services.map(service => ConfigFactory.parseString(service.asJson.spaces2).root())
  }

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val openAPIsConfig = config.rootAs[OpenAPIServicesConfig]
    val serviceDefinitionConfig = config.getList("services").render(ConfigRenderOptions.concise())
    val swaggerServices =
      CirceUtil.decodeJsonUnsafe[List[SwaggerService]](serviceDefinitionConfig, "Failed to parse service config")

    //TODO: configuration
    val fixedParameters: Map[String, () => AnyRef] = Map.empty
    new SwaggerEnrichers(openAPIsConfig.rootUrl, prepareBaseEnricherCreator(config))
      .enrichers(swaggerServices, Nil, fixedParameters)
      .map(service => ComponentDefinition(service.name, service.service, docsUrl = Option(service.documentation))).toList
  }

  protected def prepareBaseEnricherCreator(config: Config): BaseSwaggerEnricherCreator = {
    val clientConfig = config.getAs[HttpClientConfig]("httpClientConfig").getOrElse(DefaultHttpClientConfig())
    BaseSwaggerEnricherCreator(clientConfig)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
