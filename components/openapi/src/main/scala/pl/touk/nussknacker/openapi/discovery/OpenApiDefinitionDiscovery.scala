package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.cache.SingleValueCache
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import sttp.client3.basicRequest
import sttp.model.Uri

import java.io.File
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.control.NonFatal

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

class SwaggerOpenApiDefinitionDiscovery()(implicit httpBeProvider: HttpBackendProvider)
    extends LazyLogging
    with OpenApiDefinitionDiscovery {

  override def getServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    val discoveryUrl = openAPIsConfig.url
    val definition = if (discoveryUrl.getProtocol == "file") {
      ResourceLoader.load(new File(discoveryUrl.getPath))
    } else {
      val httpBackend = httpBeProvider.httpBackendForEc(ExecutionContext.global)
      try {
        Await
          .result(basicRequest.get(Uri(discoveryUrl.toURI)).send(httpBackend), 20 seconds)
          .body
          .fold(left => throw new IllegalStateException(s"Invalid response from discovery API: $left"), identity)
      } finally {
        try {
          Await.result(httpBackend.close(), 5 seconds)
        } catch {
          case NonFatal(ex) =>
            logger.warn(s"Could not close HTTP backend, this may result in leaked resources: ${ex.getMessage}", ex)
        }
      }
    }
    SwaggerParser.parse(definition, openAPIsConfig)
  }

}

class CachingOpenApiDefinitionDiscovery(
    discovery: OpenApiDefinitionDiscovery,
    cacheTtl: FiniteDuration,
) extends OpenApiDefinitionDiscovery {

  @transient private lazy val servicesCache = new SingleValueCache[List[Validated[ServiceParseError, SwaggerService]]](
    expireAfterAccess = None,
    expireAfterWrite = Some(cacheTtl)
  )

  override def getServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = servicesCache.getOrCreate {
    discovery.getServices(openAPIsConfig)
  }

}
