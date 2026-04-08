package pl.touk.nussknacker.openapi.discovery

import cats.data.Validated
import cats.data.Validated.Valid
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, PlainPart, ServiceName, SwaggerService}
import pl.touk.nussknacker.openapi.parser.ServiceParseError

import java.net.URL
import java.nio.file.Files
import scala.concurrent.duration.DurationInt

class CachingOpenApiDefinitionDiscoveryTest extends AnyFunSuite with Matchers with Eventually {

  private val testConfig = OpenAPIServicesConfig(new URL("http://example.com/openapi.json"))

  test("should use stale in-memory definitions when refresh fails") {
    val service = sampleService("from-in-memory-cache")
    val discovery = new StubOpenApiDefinitionDiscovery(
      List(
        Right(List(service)),
        Left(new IllegalStateException("temporary outage"))
      )
    )
    val cachingDiscovery = new CachingOpenApiDefinitionDiscovery(
      discovery,
      10.millis,
      cacheFileLocation = None
    )

    cachingDiscovery.getValidServices(testConfig) shouldBe List(service)
    eventually(timeout(Span(1, Seconds)), interval(Span(10, Millis))) {
      cachingDiscovery.getValidServices(testConfig) shouldBe List(service)
      cachingDiscovery.lastRefreshFailed shouldBe true
      discovery.calls shouldBe 2
    }
  }

  test("should load stale definitions from file cache when refresh fails") {
    val cacheDirectory = Files.createTempDirectory("openapi-definition-cache")
    val cacheFile      = cacheDirectory.resolve("services.json")
    try {
      val service = sampleService("from-file-cache")
      val initialDiscovery = new StubOpenApiDefinitionDiscovery(
        List(Right(List(service)))
      )
      val writingCacheDiscovery = new CachingOpenApiDefinitionDiscovery(
        initialDiscovery,
        10.millis,
        cacheFileLocation = Some(cacheFile.toString)
      )
      writingCacheDiscovery.getValidServices(testConfig) shouldBe List(service)

      val failingDiscovery = new StubOpenApiDefinitionDiscovery(
        List(Left(new IllegalStateException("definition endpoint down")))
      )
      val readingCacheDiscovery = new CachingOpenApiDefinitionDiscovery(
        failingDiscovery,
        10.millis,
        cacheFileLocation = Some(cacheFile.toString)
      )

      readingCacheDiscovery.getValidServices(testConfig) shouldBe List(service)
      failingDiscovery.calls shouldBe 1
    } finally {
      Files.deleteIfExists(cacheFile)
      Files.deleteIfExists(cacheDirectory)
    }
  }

  private def sampleService(name: String): SwaggerService =
    SwaggerService(
      name = ServiceName(name),
      categories = Nil,
      documentation = None,
      pathParts = List(PlainPart("customers")),
      parameters = Nil,
      responseSwaggerType = None,
      method = "GET",
      servers = List("http://example.com"),
      securities = Nil,
      requestContentType = None
    )

  private class StubOpenApiDefinitionDiscovery(results: List[Either[Throwable, List[SwaggerService]]])
      extends OpenApiDefinitionDiscovery {

    private var nextResults = results

    var calls: Int = 0

    override def getServices(
        openAPIsConfig: OpenAPIServicesConfig
    ): List[Validated[ServiceParseError, SwaggerService]] = {
      calls += 1
      val result = nextResults match {
        case head :: tail =>
          nextResults = tail
          head
        case Nil =>
          throw new IllegalStateException("No configured discovery result for this invocation")
      }
      result match {
        case Right(services) => services.map(Valid(_))
        case Left(ex)        => throw ex
      }
    }

  }

}
