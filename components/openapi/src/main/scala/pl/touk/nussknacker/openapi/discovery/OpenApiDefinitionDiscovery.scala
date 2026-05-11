package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.cache.SingleValueCache
import pl.touk.nussknacker.http.backend.HttpClientConfig
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import sttp.client3.{basicRequest, SttpBackend}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Uri

import java.io.File
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

trait OpenApiDefinitionDiscovery extends LazyLogging {
  def getServices(openAPIsConfig: OpenAPIServicesConfig): List[Validated[ServiceParseError, SwaggerService]]

  def getValidServices(openAPIsConfig: OpenAPIServicesConfig): List[SwaggerService] = {
    val services = getServices(openAPIsConfig)
    logErrors(services)
    services.collect { case Valid(service) => service }
  }

  private def logErrors(services: List[Validated[ServiceParseError, SwaggerService]]): Unit = {
    val errors = services.collect { case Invalid(serviceError) =>
      s"${serviceError.name.value} (${serviceError.errors.toList.mkString(", ")})"
    }
    if (errors.nonEmpty) {
      logger.warn(s"Failed to parse following services: ${errors.mkString(", ")}")
    }
  }

}

object SwaggerOpenApiDefinitionDiscovery
    extends SwaggerOpenApiDefinitionDiscovery()(
      AsyncHttpClientFutureBackend.usingClient(
        new DefaultAsyncHttpClient(
          HttpClientConfig(
            timeout = Some(5 seconds),
            connectTimeout = Some(10 seconds),
            maxPoolSize = Some(1),
            None,
            None,
            None,
            None,
            None,
          ).toAsyncHttpClientConfig(None).build()
        )
      )
    )

class SwaggerOpenApiDefinitionDiscovery(implicit val httpBackend: SttpBackend[Future, Any])
    extends LazyLogging
    with OpenApiDefinitionDiscovery {

  override def getServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    val discoveryUrl = openAPIsConfig.url
    val definition = if (discoveryUrl.getProtocol == "file") {
      ResourceLoader.load(new File(discoveryUrl.getPath))
    } else {
      Await
        .result(basicRequest.get(Uri(discoveryUrl.toURI)).send(httpBackend), 20 seconds)
        .body
        .fold(left => throw new IllegalStateException(s"Invalid response from discovery API: $left"), identity)
    }
    SwaggerParser.parse(definition, openAPIsConfig)
  }

}

class CachingOpenApiDefinitionDiscovery(
    discovery: OpenApiDefinitionDiscovery,
    openAPIsConfig: OpenAPIServicesConfig,
) extends OpenApiDefinitionDiscovery {

  @transient private lazy val servicesCache = new SingleValueCache[List[Validated[ServiceParseError, SwaggerService]]](
    expireAfterAccess = None,
    expireAfterWrite = Some(openAPIsConfig.openApiServicesDiscoveryCacheTtl)
  )

  override def getServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = servicesCache.getOrCreate {
    discovery.getServices(openAPIsConfig)
  }

}
