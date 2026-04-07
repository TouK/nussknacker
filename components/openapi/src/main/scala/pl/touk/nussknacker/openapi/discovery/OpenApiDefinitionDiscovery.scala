package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.decode
import io.circe.syntax._
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.cache.SingleValueCache
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import sttp.client3.basicRequest
import sttp.model.Uri

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption, StandardOpenOption}
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
    cacheFileLocation: Option[String] = None,
) extends OpenApiDefinitionDiscovery {

  import CachingOpenApiDefinitionDiscovery._

  private type Services = List[Validated[ServiceParseError, SwaggerService]]

  @transient private lazy val servicesCache = new SingleValueCache[Services](
    expireAfterAccess = None,
    expireAfterWrite = Some(cacheTtl)
  )

  @transient private lazy val lastValidDefinitionCache = new SingleValueCache[Services](
    expireAfterAccess = None,
    expireAfterWrite = None
  )

  override def getServices(
      openAPIsConfig: OpenAPIServicesConfig
  ): Services = {
    servicesCache.get().getOrElse {
      refreshServices(openAPIsConfig)
    }
  }

  private def refreshServices(openAPIsConfig: OpenAPIServicesConfig): Services = {
    try {
      val refreshedServices = discovery.getServices(openAPIsConfig)
      servicesCache.put(refreshedServices)
      putLastValidDefinition(refreshedServices)
      persistLastValidDefinition(refreshedServices)
      refreshedServices
    } catch {
      case NonFatal(ex) =>
        getCachedServicesFallback match {
          case Some(CachedServices(cachedServices, FallbackSource.MemoryCache)) =>
            logger.warn(
              s"Could not refresh OpenAPI definitions from [${openAPIsConfig.url}], using cache",
              ex
            )
            servicesCache.put(cachedServices)
            cachedServices
          case Some(CachedServices(cachedServices, FallbackSource.FileCache)) =>
            logger.warn(
              s"Could not refresh OpenAPI definitions from [${openAPIsConfig.url}], using file cache",
              ex
            )
            servicesCache.put(cachedServices)
            cachedServices
          case None =>
            throw ex
        }
    }
  }

  private def putLastValidDefinition(services: Services): Unit = {
    if (services.exists(_.isValid)) {
      lastValidDefinitionCache.put(services)
    }
  }

  private def getCachedServicesFallback: Option[CachedServices] = {
    lastValidDefinitionCache.get().map(CachedServices(_, FallbackSource.MemoryCache)).orElse {
      loadLastValidDefinitionFromFile().map { servicesFromFile =>
        val wrappedServices: Services = servicesFromFile.map(Valid(_))
        lastValidDefinitionCache.put(wrappedServices)
        CachedServices(wrappedServices, FallbackSource.FileCache)
      }
    }
  }

  private def persistLastValidDefinition(services: Services): Unit = {
    val validServicesToPersist = services.collect { case Valid(service) => service }
    if (validServicesToPersist.nonEmpty) {
      cacheFileLocation.foreach { fileLocation =>
        val targetPath = Path.of(fileLocation)
        try {
          Option(targetPath.getParent).foreach(Files.createDirectories(_))
          val temporaryPath =
            Option(targetPath.getParent)
              .map(Files.createTempFile(_, s".${targetPath.getFileName.toString}.", ".tmp"))
              .getOrElse(Files.createTempFile(".openapi-definition-cache.", ".tmp"))
          try {
            Files.writeString(
              temporaryPath,
              validServicesToPersist.asJson.noSpaces,
              StandardCharsets.UTF_8,
              StandardOpenOption.TRUNCATE_EXISTING
            )
            moveAtomicallyOrReplace(temporaryPath, targetPath)
          } finally {
            Files.deleteIfExists(temporaryPath)
          }
        } catch {
          case NonFatal(ex) =>
            logger.warn(s"Could not persist OpenAPI definitions cache to [$fileLocation]", ex)
        }
      }
    }
  }

  private def loadLastValidDefinitionFromFile(): Option[List[SwaggerService]] = {
    cacheFileLocation.flatMap { fileLocation =>
      val path = Path.of(fileLocation)
      if (!Files.exists(path)) {
        None
      } else {
        try {
          val jsonString = Files.readString(path, StandardCharsets.UTF_8)
          decode[List[SwaggerService]](jsonString) match {
            case Right(services) => Some(services)
            case Left(error) =>
              logger.warn(s"Could not decode OpenAPI definitions cache from [$fileLocation]: ${error.getMessage}")
              None
          }
        } catch {
          case NonFatal(ex) =>
            logger.warn(s"Could not load OpenAPI definitions cache from [$fileLocation]", ex)
            None
        }
      }
    }
  }

  private def moveAtomicallyOrReplace(source: Path, target: Path): Unit = {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case _: AtomicMoveNotSupportedException =>
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

}

object CachingOpenApiDefinitionDiscovery {

  private final case class CachedServices(
      services: List[Validated[ServiceParseError, SwaggerService]],
      source: FallbackSource
  )

  private sealed trait FallbackSource

  private object FallbackSource {
    case object MemoryCache extends FallbackSource
    case object FileCache   extends FallbackSource
  }

}
