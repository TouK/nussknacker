package pl.touk.nussknacker.http

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.jdk.CollectionConverters._

class HttpEnricherHeadersTest extends HttpEnricherTestSuite {

  val configuredHeadersEnricher: ComponentDefinition = {
    val configuredAdditionalHeadersHttpConfig = ConfigFactory.parseString(
      s"""
         |{
         |  security: [
         |    {
         |      in: "header"
         |      name: "configured_header_1_key"
         |      value: "configured_header_1_value"
         |    },
         |    {
         |      in: "header"
         |      name: "configured_header_2_key"
         |      value: "configured_header_2_value"
         |    },
         |    {
         |      in: "cookie"
         |      name: "configured_cookie_key"
         |      value: "configured_cookie_value"
         |    }
         |  ]
         |}
         |""".stripMargin
    )
    new HttpEnricherComponentProvider()
      .create(configuredAdditionalHeadersHttpConfig, ProcessObjectDependencies.withConfig(ConfigFactory.empty()))
      .head
      .copy(name = "configuredSecurityInHeadersHttp")
  }

  override protected lazy val additionalComponents: List[ComponentDefinition] = List(configuredHeadersEnricher)

  test("makes request with lazily evaluated header") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .withHeader("spel_header_1_key", new EqualToPattern("spel_header_1_value"))
        .withHeader("spel_header_2_key", new EqualToPattern("input_header_2_key"))
        .willReturn(aResponse().withStatus(200))
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http-node-id",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{ spel_header_1_key : 'spel_header_1_value', spel_header_2_key : #input }".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("input_header_2_key"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request with header from config") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .withHeader("configured_header_1_key", new EqualToPattern("configured_header_1_value"))
        .withHeader("configured_header_2_key", new EqualToPattern("configured_header_2_value"))
        .willReturn(aResponse().withStatus(200))
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevant value"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  // TODO http: is this ok? even if we validate not overriding we cant ensure that in runtime
  test("makes request with header from parameter that overwrites configured header") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .withHeader("configured_header_1_key", new EqualToPattern("overwriten_spel_header_1_value"))
        .willReturn(aResponse().withStatus(200))
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{ configured_header_1_key : 'overwriten_spel_header_1_value' }".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevant value"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("makes request with cookie from config") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .withCookie("configured_cookie_key", new EqualToPattern("configured_cookie_value"))
        .willReturn(aResponse().withStatus(200))
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevant value"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

  test("returns received headers from response") {
    wireMock.stubFor(
      get(urlEqualTo("/header-test"))
        .willReturn(aResponse().withStatus(200).withHeader("response_header_key", "response_header_value"))
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.headers".spel)

    val result = runner
      .runWithData[String, java.util.Map[String, String]](scenario, List("irrelevant value"))
      .validValue
      .successes
      .head

    result.asScala should contain("response_header_key" -> "response_header_value")
  }

  // TODO http: is this behaviour ok? or should we treat {} as empty map to not confuse users?
  test("returns error when using list in headers parameter") {
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http",
        "httpOutput",
        configuredHeadersEnricher.name,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{}".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.response.headers".spel)

    val result = runner
      .runWithData[String, java.util.Map[String, String]](scenario, List("irrelevant value"))
      .invalidValue
      .head

    result should matchPattern {
      case ExpressionParserCompilationError(
            "Bad expression type, expected: Map[String,String], found: List[Unknown]({})",
            _,
            Some(ParameterName("Headers")),
            _,
            _
          ) =>
    }
  }

}
