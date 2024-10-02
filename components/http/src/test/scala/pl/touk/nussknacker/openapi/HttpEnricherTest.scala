package pl.touk.nussknacker.openapi

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.http.HttpEnricherComponentProvider
import pl.touk.nussknacker.test.AvailablePortFinder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.jdk.CollectionConverters._

class HttpEnricherTest extends AnyFunSuite with LazyLogging with BeforeAndAfterAll with FlinkSpec with Matchers {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  private val wireMock: WireMockServer = {
    val server = AvailablePortFinder.withAvailablePortsBlocked(1)(l => {
      new WireMockServer(
        WireMockConfiguration
          .wireMockConfig()
          .port(l.head)
      )
    })
    server.start()
    server
  }

  override protected def afterAll(): Unit = {
    try {
      wireMock.stop()
    } finally {
      super.afterAll()
    }
  }

  private lazy val additionalComponents: List[ComponentDefinition] =
    new HttpEnricherComponentProvider()
      .create(ConfigFactory.empty(), ProcessObjectDependencies.withConfig(ConfigFactory.empty()))

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(additionalComponents)
    .build()

  test("returns body as value decoded from json or plain string") {
    forAll(
      Table(
        ("body", "expectedBodyRuntimeValue"),
        (""" "string" """, "string"),
        (" true ", true),
        ("123", java.math.BigDecimal.valueOf(123)),
        ("null", null),
        ("[]", List.empty),
        ("{}", Map.empty),
        ("<html></html>", "<html></html>"),
        (TestData.recordWithAllTypesNestedAsJson, TestData.recordWithAllTypesNestedAsComparableAsNuRuntimeValue)
      )
    ) { case (body, expected) =>
      wireMock.stubFor(
        get(urlEqualTo("/types-test")).willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
      )
      val scenario = ScenarioBuilder
        .streaming("id")
        .source("start", TestScenarioRunner.testDataSource)
        .enricher(
          "http",
          "httpOutput",
          "http",
          "URL"         -> s"'${wireMock.baseUrl()}/types-test'".spel,
          "HTTP Method" -> "'GET'".spel,
          "Headers"     -> "".spel,
        )
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.body".spel)

      val result = runner
        .runWithData[String, Any](scenario, List("irrelevantInput"))
        .validValue
        .successes
        .head
      deepToScala(result) shouldBe expected
    }
  }

  test("returns status code and status text") {
    forAll(
      Table(
        ("code", "statusCode"),
        (200, "OK"),
        (400, "Bad Request"),
        (500, "Server Error"),
      )
    ) { case (code, statusText) =>
      wireMock.stubFor(
        get(urlEqualTo("/code-test")).willReturn(
          aResponse().withStatus(code)
        )
      )
      val scenario = ScenarioBuilder
        .streaming("id")
        .source("start", TestScenarioRunner.testDataSource)
        .enricher(
          "http",
          "httpOutput",
          "http",
          "URL"         -> s"'${wireMock.baseUrl()}/code-test'".spel,
          "HTTP Method" -> "'GET'".spel,
          "Headers"     -> "".spel,
        )
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput".spel)

      val result = runner
        .runWithData[String, java.util.Map[String, Any]](scenario, List("irrelevantInput"))
        .validValue
        .successes
        .head

      result.asScala should contain allOf ("statusCode" -> code, "statusText" -> statusText)
    }
  }

  test("makes request with given response headers and returns response headers") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .withHeader("testRequestHeaderKey", new EqualToPattern("testRequestHeaderValue"))
        .willReturn(
          aResponse().withStatus(200).withHeader("testResponseHeaderKey", "testResponseHeaderValue")
        )
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        "http",
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{ 'testRequestHeaderKey' : 'testRequestHeaderValue' }".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.headers".spel)

    val result = runner
      .runWithData[String, java.util.Map[String, String]](scenario, List("irrelevantInput"))
      .validValue
      .successes
      .head

    result.asScala should contain("testResponseHeaderKey" -> "testResponseHeaderValue")
  }

  private def deepToScala(obj: Any): Any = obj match {
    case map: java.util.Map[_, _] =>
      map.asScala.map { case (key, value) => (deepToScala(key), deepToScala(value)) }.toMap
    case list: java.util.List[_] =>
      list.asScala.map(deepToScala).toList
    case set: java.util.Set[_] =>
      set.asScala.map(deepToScala).toSet
    case array: Array[_] =>
      array.map(deepToScala)
    case other =>
      other
  }

  object TestData {

    val recordWithAllTypesNestedAsJson: String =
      """
        |{
        |  "string": "this is a string",
        |  "number": 123,
        |  "decimal": 123.456,
        |  "booleanTrue": true,
        |  "nullValue": null,
        |  "object": {
        |    "nestedString": "nested value",
        |    "nestedArray": [1, 2, 3],
        |    "nestedObject": {
        |      "innerKey": "innerValue"
        |    }
        |  },
        |  "arrayOfObjects": [
        |    {"key1": "value1"},
        |    {"key2": "value2"}
        |  ],
        |  "array": [1, "string", true, null, {"innerArrayObject": [1, 2, 3]}]
        |}
        |""".stripMargin

    val recordWithAllTypesNestedAsComparableAsNuRuntimeValue: Map[String, Any] = Map(
      "string"      -> "this is a string",
      "number"      -> java.math.BigDecimal.valueOf(123),
      "decimal"     -> java.math.BigDecimal.valueOf(123.456),
      "booleanTrue" -> true,
      "nullValue"   -> null,
      "object" -> Map(
        "nestedString" -> "nested value",
        "nestedArray" -> List(
          java.math.BigDecimal.valueOf(1),
          java.math.BigDecimal.valueOf(2),
          java.math.BigDecimal.valueOf(3)
        ),
        "nestedObject" -> Map(
          "innerKey" -> "innerValue"
        )
      ),
      "arrayOfObjects" -> List(
        Map("key1" -> "value1"),
        Map("key2" -> "value2")
      ),
      "array" -> List(
        java.math.BigDecimal.valueOf(1),
        "string",
        true,
        null,
        Map(
          "innerArrayObject" -> List(
            java.math.BigDecimal.valueOf(1),
            java.math.BigDecimal.valueOf(2),
            java.math.BigDecimal.valueOf(3)
          )
        )
      )
    )

  }

}
