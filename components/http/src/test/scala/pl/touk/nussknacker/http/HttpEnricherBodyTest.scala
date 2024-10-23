package pl.touk.nussknacker.http

import com.github.tomakehurst.wiremock.client.WireMock._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class HttpEnricherBodyTest extends HttpEnricherTestSuite {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  test("returns response body as value based on content type header with fallback to passing it a string") {
    forAll(
      Table(
        ("contentType", "body", "expectedBodyRuntimeValue"),
        ("application/json", """ "string" """, "string"),
        ("application/json", "true", true),
        ("application/json; charset=UTF-8", "true", true),
        (
          "application/json",
          TestData.recordWithAllTypesNestedAsJson,
          TestData.recordWithAllTypesNestedAsComparableAsNuRuntimeValue
        ),
        ("text/plain", "string", "string"),
        ("text/plain", "string", "string"),
        ("text/html", "<html>value</html>", "<html>value</html>"),
      )
    ) { case (contentType, body, expected) =>
      wireMock.stubFor(
        get(urlEqualTo("/body-test")).willReturn(
          aResponse()
            .withHeader("Content-Type", contentType)
            .withBody(body)
        )
      )
      val scenario = ScenarioBuilder
        .streaming("id")
        .source("start", TestScenarioRunner.testDataSource)
        .enricher(
          "http-node-id",
          "httpOutput",
          noConfigHttpEnricherName,
          "URL"         -> s"'${wireMock.baseUrl()}/body-test'".spel,
          "HTTP Method" -> "'GET'".spel,
          "Body Type"   -> "'None'".spel,
        )
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.body".spel)

      val result = runner
        .runWithData[String, AnyRef](scenario, List("irrelevantInput"))
        .validValue
        .successes
        .head
      deepToScala(result) shouldBe expected
    }
  }

  test("makes request without body") {
    wireMock.stubFor(
      post(urlEqualTo("/body-test"))
        .withRequestBody(absent())
        .willReturn(aResponse())
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        noConfigHttpEnricherName,
        "URL"         -> s"'${wireMock.baseUrl()}/body-test'".spel,
        "HTTP Method" -> "'POST'".spel,
        "Body Type"   -> "'None'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevantInput"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request with plain text body") {
    wireMock.stubFor(
      post(urlEqualTo("/body-test"))
        .withRequestBody(equalTo("Plain text body"))
        .withHeader("Content-Type", equalTo("text/plain; charset=UTF-8"))
        .willReturn(aResponse())
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        noConfigHttpEnricherName,
        "URL"         -> s"'${wireMock.baseUrl()}/body-test'".spel,
        "HTTP Method" -> "'POST'".spel,
        "Body Type"   -> "'Plain Text'".spel,
        "Body"        -> "'Plain text body'".spel
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevantInput"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request with json body parsed from spel") {
    forAll(
      Table(
        ("spelJsonBody", "stringJsonBody"),
        ("null".spel, "null"),
        ("{}".spel, "[]"),
        ("{1,2,3}".spel, "[1,2,3]"),
        (TestData.recordWithAllTypesNestedAsSpelString, TestData.recordWithAllTypesNestedAsJson)
      )
    ) {
      case (spelJsonBody, requestJsonBodyString) => {
        wireMock.stubFor(
          post(urlEqualTo("/body-test"))
            .withRequestBody(equalToJson(requestJsonBodyString))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .willReturn(
              aResponse().withStatus(200)
            )
        )
        val scenario = ScenarioBuilder
          .streaming("id")
          .source("start", TestScenarioRunner.testDataSource)
          .enricher(
            "http",
            "httpOutput",
            noConfigHttpEnricherName,
            "URL"         -> s"'${wireMock.baseUrl()}/body-test'".spel,
            "HTTP Method" -> "'POST'".spel,
            "Body Type"   -> "'JSON'".spel,
            "Body"        -> spelJsonBody
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

        val result = runner
          .runWithData[String, Integer](scenario, List("irrelevantInput"))
          .validValue
          .successes
          .head

        result shouldBe 200
      }
    }
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

    val recordWithAllTypesNestedAsSpelString: Expression =
      """
        |{
        |  "string": "this is a string",
        |  "number": 123,
        |  "decimal": 123.456,
        |  "booleanTrue": true,
        |  "nullValue": null,
        |  "object": {
        |    "nestedString": "nested value",
        |    "nestedArray": {1, 2, 3},
        |    "nestedObject": {
        |      "innerKey": "innerValue"
        |    }
        |  },
        |  "arrayOfObjects": {
        |    {"key1": "value1"},
        |    {"key2": "value2"}
        |  },
        |  "array": {1, "string", true, null, {"innerArrayObject": {1, 2, 3}}}
        |}
        |""".stripMargin.spel

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
