package pl.touk.nussknacker.http

import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class HttpEnricherURLTest extends HttpEnricherTestSuite {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  lazy val configuredRootURLEnricher: ComponentDefinition = {
    val configuredAdditionalHeadersHttpConfig = ConfigFactory.parseString(
      s"""
         |{
         |  rootUrl: "${wireMock.baseUrl()}",
         |  security: [
         |    {
         |      in: "query"
         |      name: "configured_query_1_key"
         |      value: "configured_query_1_value"
         |    }
         |  ]
         |}
         |""".stripMargin
    )
    new HttpEnricherComponentProvider()
      .create(configuredAdditionalHeadersHttpConfig, ProcessObjectDependencies.withConfig(ConfigFactory.empty()))
      .head
      .copy(name = "configured-root-url-and-query-security-key-http")
  }

  override protected lazy val additionalComponents: List[ComponentDefinition] = List(configuredRootURLEnricher)

  test("makes request under specified url with configured root url and query params") {
    wireMock.stubFor(
      get(urlEqualTo("/url-test?spel_query_key_1=input_query_value_1&configured_query_1_key=configured_query_1_value"))
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
        configuredRootURLEnricher.name,
        "URL"              -> s"'/url-test'".spel,
        "Query Parameters" -> "{ spel_query_key_1 : #input }".spel,
        "HTTP Method"      -> "'GET'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("input_query_value_1"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request with query parameters with spel value and spel list") {
    wireMock.stubFor(
      get(
        urlEqualTo(
          "/url-test?spel_query_key_1=spel_query_value_1_1&spel_query_key_1=spel_query_value_1_2&spel_query_key_2=spel_query_value_2"
        )
      )
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
        "URL" -> s"'${wireMock.baseUrl}/url-test'".spel,
        "Query Parameters" ->
          """
            |{
            |   spel_query_key_1 : {'spel_query_value_1_1', 'spel_query_value_1_2'},
            |   spel_query_key_2 : 'spel_query_value_2'
            |}
            |""".stripMargin.spel,
        "HTTP Method" -> "'GET'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("input_query_value_1"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request under specified url with encoded query params") {
    wireMock.stubFor(
      get(urlEqualTo("/url-test?spel_query_unencoded_header_+!%23=spel_query_unencoded_header_+!%23"))
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
        "URL"              -> s"'${wireMock.baseUrl}/url-test'".spel,
        "Query Parameters" -> "{ 'spel_query_unencoded_header_ !#' : 'spel_query_unencoded_header_ !#' }".spel,
        "HTTP Method"      -> "'GET'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("input_query_value_1"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("validates url during parameters validation") {
    forAll(
      Table(
        ("invalidUrlExpr", "expectedErrorMessage"),
        ("'noturl'".spel, "Invalid URL: no protocol: noturl"),
        ("'badprotocol://'".spel, "Invalid URL: unknown protocol: badprotocol"),
        ("'http://'".spel, "Invalid URL: Expected scheme-specific part at index 5: http:"),
        (
          "'http://host_with_space in_authority'".spel,
          "Invalid URL: Illegal character in authority at index 7: http://host_with_space in_authority"
        )
      )
    ) { (invalidUrlExpr, errorMessage) =>
      val scenario = ScenarioBuilder
        .streaming("id")
        .source("start", TestScenarioRunner.testDataSource)
        .enricher(
          "http",
          "httpOutput",
          noConfigHttpEnricherName,
          "URL"         -> invalidUrlExpr,
          "HTTP Method" -> "'GET'".spel
        )
        .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput".spel)

      val errors = runner
        .runWithData[String, Integer](scenario, List("irrelevant"))
        .invalidValue

      errors.head shouldBe CustomNodeError("http", errorMessage, Some(ParameterName("URL")))
    }
  }

}
