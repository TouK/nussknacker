package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters

trait EventGeneratorSourceTestingApiHttpServiceSpec extends TestingApiHttpServiceSpec {

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
      .emptySink("end", "monitor")

  override protected def expectedSourceTestingParametersJson: String =
    "[]"

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

}
