package pl.touk.nussknacker.openapi

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.http.HttpEnricherComponentProvider
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

//   TODO: test for encoding, errors
//   TODO: other test for response headers
//   TODO: test for overriding headers
class HttpEnricherHeadersTest extends HttpEnricherTestSuite {

  val configuredHeaderHttpEnricherName = "configured-headers-http"

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
         |    }
         |  ]
         |}
         |""".stripMargin
    )
    new HttpEnricherComponentProvider()
      .create(configuredAdditionalHeadersHttpConfig, ProcessObjectDependencies.withConfig(ConfigFactory.empty()))
      .head
      .copy(name = configuredHeaderHttpEnricherName)
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
        noConfigHttpEnricherName,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{ spel_header_1_key : 'spel_header_1_value', spel_header_2_key : #input }".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.statusCode".spel)

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
        configuredHeaderHttpEnricherName,
        "URL"         -> s"'${wireMock.baseUrl()}/header-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput.statusCode".spel)

    val result = runner
      .runWithData[String, Integer](scenario, List("irrelevant value"))
      .validValue
      .successes
      .head

    result shouldBe 200
  }

}
