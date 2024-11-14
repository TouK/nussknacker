package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.LayoutData
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.{Dimensions, StickyNoteAddRequest}

import java.util.UUID

class StickyNotesApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  private val exampleScenarioName = UUID.randomUUID().toString

  private val exampleScenario = ScenarioBuilder
    .requestResponse(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private def stickyNoteToAdd(versionId: VersionId): StickyNoteAddRequest =
    StickyNoteAddRequest(versionId, "", LayoutData(0, 1), "#aabbcc", Dimensions(300, 200), None)

  "The GET stickyNotes for scenario" - {
    "return no notes if nothing was created" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/stickyNotes?scenarioVersionId=0")
        .Then()
        .statusCode(200)
        .equalsJsonBody("[]")
    }

    "return 404 if no scenario with given name exists" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/stickyNotes?scenarioVersionId=0")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $exampleScenarioName found")
    }

    "return zero notes for scenarioVersion=1 if notes were added in scenarioVersion=2" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          val updatedProcess = updateScenario(ProcessName(exampleScenarioName), exampleScenario)
          addStickyNote(ProcessName(exampleScenarioName), stickyNoteToAdd(updatedProcess.newVersion.get))
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/stickyNotes?scenarioVersionId=1")
        .Then()
        .statusCode(200)
        .equalsJsonBody("[]")
    }

    // TODO more tests

  }

}