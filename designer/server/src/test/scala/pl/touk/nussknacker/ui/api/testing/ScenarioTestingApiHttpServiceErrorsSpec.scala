package pl.touk.nussknacker.ui.api.testing

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class ScenarioTestingApiHttpServiceErrorsSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with NuRestAssureExtensions {

  private val missingSourceId = "missing source"

  private val scenarioWithMissingSource: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario with missing source")
      .source(missingSourceId, "missing source", "a parameter" -> "{'test'}".spel)
      .emptySink("end", "monitor")

  "The endpoint for adhoc validate should" - {
    "return errors on missing source" in {
      given()
        .applicationState {
          createSavedScenario(scenarioWithMissingSource)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          ScenarioTestValidationRequest(
            testData = ScenarioTestData.WithParameters(
              TestSourceParameters(missingSourceId, Map(ParameterName("a parameter") -> "{'123'}".spel))
            ),
            scenarioGraph = toScenarioGraph(scenarioWithMissingSource)
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${scenarioWithMissingSource.name}/validate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Requested test parameters from source [missing source] that is not valid. Errors: MissingSourceFactory(missing source,missing source)"
        )
    }
  }

}
