package pl.touk.nussknacker.ui.api.testing

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import sttp.client3.Response

trait EventGeneratorSourceTestingApiHttpServiceSpec
    extends ScenarioTestingApiHttpServiceGenericSpec
    with EitherValuesDetailedMessage
    with Matchers {

  // We need to add flinkBaseUnbounded components to the classpath in order to test EventGenerator
  override def designerRawConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources(
      "config/business-cases/simple-streaming-use-case-designer.conf"
    )
  )

  protected def eventGeneratorValue: Expression

  override protected def exampleScenarioSourceId = "eventGeneratorSourceId"

  override protected def exampleScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario_2")
      .source(
        exampleScenarioSourceId,
        "event-generator",
        "count"    -> "1".spel,
        "value"    -> eventGeneratorValue,
        "schedule" -> "T(java.time.Duration).parse('PT1M')".spel,
      )
      // We add filtering logic to ensure that types are correctly verified during testing
      .filter("filter", filteringExpression)
      .emptySink("end", "dead-end")

  protected def filteringExpression: Expression

  override protected def verifyTestFromFileWithCorrectTestDataResponse(response: Response[String]): Assertion = {
    response.bodyAsJson.hcursor
      .downField("counts")
      .downField(exampleScenarioSourceId)
      .downField("all")
      .as[Int]
      .rightValue shouldBe 3
  }

}
