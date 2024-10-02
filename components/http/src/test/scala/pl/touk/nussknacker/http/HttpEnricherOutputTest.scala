package pl.touk.nussknacker.http

import com.github.tomakehurst.wiremock.client.WireMock._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  DynamicComponent,
  OutputVariableNameValue
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig
import pl.touk.nussknacker.http.enricher.HttpEnricher.{BodyType, HttpMethod}
import pl.touk.nussknacker.http.enricher.HttpEnricherFactory
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

// TODO: add test for request data
// TODO: add test for typing
class HttpEnricherOutputTest extends HttpEnricherTestSuite {

  test("returns request and response data as seperate fields") {
    wireMock.stubFor(
      get(urlEqualTo("/code-test")).willReturn(
        aResponse().withStatus(200).withHeader("response_header_key_1", "response_header_value_1")
      )
    )
    val scenario = ScenarioBuilder
      .streaming("id")
      .source("start", TestScenarioRunner.testDataSource)
      .enricher(
        "http-node-id",
        "httpOutput",
        noConfigHttpEnricherName,
        "URL"         -> s"'${wireMock.baseUrl()}/code-test'".spel,
        "HTTP Method" -> "'GET'".spel,
        "Headers"     -> "{ spel_header_1_key : 'spel_header_1_value' }".spel,
        "Body Type"   -> "'None'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#httpOutput".spel)

    val result = runner
      .runWithData[String, java.util.Map[String, AnyRef]](scenario, List("irrelevantInput"))
      .validValue
      .successes
      .head

    val resultMap = deepToScala(result).asInstanceOf[Map[String, AnyRef]]

    val request = resultMap("request").asInstanceOf[Map[String, AnyRef]]
    request("url") shouldBe s"${wireMock.baseUrl()}/code-test"
    request("method") shouldBe "GET"
    request("body") shouldBe null

    val requestHeaders = request("headers").asInstanceOf[Map[String, String]]
    requestHeaders("spel_header_1_key") shouldBe "spel_header_1_value"

    val response = resultMap("response").asInstanceOf[Map[String, AnyRef]]
    response("statusCode") shouldBe 200
    response("statusText") shouldBe "OK"
    response("body") shouldBe null

    val responseHeaders = response("headers").asInstanceOf[Map[String, String]]
    responseHeaders("response_header_key_1") shouldBe "response_header_value_1"
  }

  // TODO: how to test typing result from context transformation
  ignore("return typing result of request body if can be determined") {
    val enricherService =
      new HttpEnricherFactory(HttpEnricherConfig.apply(None, None, DefaultHttpClientConfig(), List(HttpMethod.GET)))
    val transformation = {
      enricherService.contextTransformation(ValidationContext.empty, List(OutputVariableNameValue("outputVar")))(
        NodeId("test")
      )(
        enricherService.TransformationStep(
          List(
            (ParameterName("URL"), DefinedLazyParameter(Typed.fromInstance("http://somehost.com"))),
            (ParameterName("Query Parameters"), DefinedLazyParameter(Typed.fromInstance(null))),
            (ParameterName("HTTP Method"), DefinedEagerParameter("GET", Typed.fromInstance("GET"))),
            (ParameterName("Headers"), DefinedLazyParameter(Typed.fromInstance(null))),
            (ParameterName("Body Type"), DefinedEagerParameter("None", Typed.fromInstance("None"))),
            (ParameterName("Body"), DefinedLazyParameter(Typed.fromInstance(null))),
          ),
          Some(HttpEnricherFactory.TransformationState.BodyTypeDeclared(BodyType.PlainText))
        )
      )
    }
    // cant match on DynamicComponent.FinalResults - no way to do it from here?
  }

}
