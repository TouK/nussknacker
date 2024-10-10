package pl.touk.nussknacker.openapi

import com.github.tomakehurst.wiremock.client.WireMock._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.jdk.CollectionConverters._

// TODO: after more test coverage divide the tests into more files for better readability?
class HttpEnricherTest extends HttpEnricherTestSuite {

  import org.scalatest.prop.TableDrivenPropertyChecks._

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
          "http-node-id",
          "httpOutput",
          noConfigHttpEnricherName,
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

}
