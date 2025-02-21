package pl.touk.nussknacker.openapi.functional

import cats.data.Validated.Valid
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.{ClassBasedTestScenarioRunner, RunResult, TestScenarioRunner}
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricher
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SingleBodyParameter}
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, VeryPatientScalaFutures}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Response, SttpBackend}
import sttp.model.{Header, HeaderNames, MediaType, StatusCode}

import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

class OpenApiScenarioIntegrationTest
    extends AnyFlatSpec
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with VeryPatientScalaFutures
    with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  // This tests don't use StubService implementation for handling requests - only for serving openapi definition. Invocation is handled by provided sttp backend.
  def withSwagger(sttpBackend: SttpBackend[Future, Any])(test: ClassBasedTestScenarioRunner => Any) =
    new StubService().withCustomerService { port =>
      test(prepareScenarioRunner(port, sttpBackend))
    }

  def withPrimitiveRequestBody(sttpBackend: SttpBackend[Future, Any])(test: ClassBasedTestScenarioRunner => Any) =
    new StubService("/customer-primitive-swagger.yaml").withCustomerService { port =>
      test(prepareScenarioRunner(port, sttpBackend, _.copy(allowedMethods = List("POST"))))
    }

  def withPrimitiveReturnType(sttpBackend: SttpBackend[Future, Any])(test: ClassBasedTestScenarioRunner => Any) =
    new StubService("/customer-primitive-return-swagger.yaml").withCustomerService { port =>
      test(prepareScenarioRunner(port, sttpBackend, _.copy(allowedMethods = List("POST"))))
    }

  def withRequestBody(sttpBackend: SttpBackend[Future, Any])(test: ClassBasedTestScenarioRunner => Any) =
    new StubService("/enrichers-with-optional-fields.yaml").withCustomerService { port =>
      test(prepareScenarioRunner(port, sttpBackend, _.copy(allowedMethods = List("POST"))))
    }

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  val stubbedBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
    case request =>
      request.headers match {
        case headers
            if headers.exists(_.name == HeaderNames.ContentType)
              && !headers.contains(Header(HeaderNames.ContentType, MediaType.ApplicationJson.toString())) =>
          Response("Unsupported media type", StatusCode.UnsupportedMediaType)
        case _ => Response.ok((s"""{"name": "Robert Wright", "id": 10, "category": "GOLD"}"""))
      }
  }

  it should "should enrich scenario with data" in withSwagger(stubbedBackend) { testScenarioRunner =>
    // given
    val data     = List("10")
    val scenario = scenarioWithEnricher(("customer_id", "#input".spel))

    // when
    val result = testScenarioRunner.runWithData(scenario, data)

    // then
    result.validValue shouldBe RunResult.success(
      TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD"))
    )
  }

  it should "call enricher with primitive request body" in withPrimitiveRequestBody(stubbedBackend) {
    testScenarioRunner =>
      // given
      val data     = List("10")
      val scenario = scenarioWithEnricher((SingleBodyParameter.name, "#input".spel))

      // when
      val result = testScenarioRunner.runWithData(scenario, data)

      // then
      result.validValue shouldBe RunResult.success(
        TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD"))
      )
  }

  it should "call enricher with request body" in withRequestBody(stubbedBackend) { testScenarioRunner =>
    // given
    val data = List("10")
    val scenario =
      scenarioWithEnricher((SingleBodyParameter.name, """{{additionalKey:"sss", primaryKey:"dfgdf"}}""".spel))

    // when
    val result = testScenarioRunner.runWithData(scenario, data)

    // then
    result.validValue shouldBe RunResult.success(
      TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD"))
    )
  }

  it should "call enricher returning string" in withPrimitiveReturnType(
    SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case _ =>
      Response.ok((s""""justAString""""))
    }
  ) { testScenarioRunner =>
    // given
    val data     = List("10")
    val scenario = scenarioWithEnricher((SingleBodyParameter.name, "#input".spel))

    // when
    val result = testScenarioRunner.runWithData(scenario, data)

    // then
    result.validValue shouldBe RunResult.success("justAString")
  }

  private def scenarioWithEnricher(params: (String, Expression)*) = {
    ScenarioBuilder
      .streaming("openapi-test")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .enricher("customer", "customer", "getCustomer", params: _*)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#customer".spel)
  }

  private def prepareScenarioRunner(
      port: Int,
      sttpBackend: SttpBackend[Future, Any],
      openAPIsConfigCustomize: OpenAPIServicesConfig => OpenAPIServicesConfig = identity
  ) = {
    val url            = new URL(s"http://localhost:$port/swagger")
    val rootUrl        = new URL(s"http://localhost:$port/customers")
    val openAPIsConfig = openAPIsConfigCustomize(OpenAPIServicesConfig(url, rootUrl = Some(rootUrl)))
    val stubComponent  = prepareStubbedComponent(sttpBackend, openAPIsConfig, url)
    // TODO: switch to liteBased after adding ability to override components there (currently there is only option to append not conflicting once) and rename class to *FunctionalTest
    TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExtraComponents(List(stubComponent))
      .build()
  }

  private def prepareStubbedComponent(
      sttpBackend: SttpBackend[Future, Any],
      openAPIsConfig: OpenAPIServicesConfig,
      url: URL
  ) = {
    val definition = IOUtils.toString(url, StandardCharsets.UTF_8)
    val services = SwaggerParser.parse(definition, openAPIsConfig).collect { case Valid(service) =>
      service
    }
    val stubbedGetCustomerOpenApiService =
      new SwaggerEnricher(url, services.head, Map.empty, (_: ExecutionContext) => sttpBackend, Nil)
    ComponentDefinition("getCustomer", stubbedGetCustomerOpenApiService)
  }

}
