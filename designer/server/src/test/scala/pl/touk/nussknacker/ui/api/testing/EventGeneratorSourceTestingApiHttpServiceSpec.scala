package pl.touk.nussknacker.ui.api.testing

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{AdhocTestParametersRequest, TestSourceParameters}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

trait EventGeneratorSourceTestingApiHttpServiceSpec extends TestingApiHttpServiceSpec {

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
      .emptySink("end", "dead-end")

  override protected def parametersProvidedForDryRun: String =
    AdhocTestParametersRequest(
      sourceParameters = validParameters,
      scenarioGraph = toScenarioGraph(exampleScenario)
    ).asJson.toString()

  override protected def expectedSourceTestingParametersJson: String = ""

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map.empty)

  override protected def invalidParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(ParameterName("someParam") -> "0L".spel))

  override protected def expectedValidationErrorsOnInvalidParametersJson: String =
    """
      |[
      |  {
      |    "typ": "RedundantParameters",
      |    "message": "Redundant parameters",
      |    "description": "Please omit redundant parameters: ParameterName(someParam)",
      |    "fieldName": null,
      |    "errorType": "SaveAllowed",
      |    "details": null
      |  }
      |]
      |""".stripMargin

  override protected def expectedTestParametersJson: String = {
    s"""
       |[
       |  {
       |    "sourceId": "$exampleScenarioSourceId",
       |    "parameters": []
       |  }
       |]
       |""".stripMargin
  }

}
