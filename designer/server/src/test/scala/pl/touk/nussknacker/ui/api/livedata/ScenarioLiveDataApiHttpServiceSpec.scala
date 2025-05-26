package pl.touk.nussknacker.ui.api.livedata

import io.circe.Json
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.{LiveDataPreviewSupported, NoLiveDataPreviewSupport}
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.{LiveData, LiveDataError}
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.test.{
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails,
  WithTestHttpClient
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}

import scala.concurrent.Future

class ScenarioLiveDataApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithTestHttpClient
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val exampleScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario_2")
      .source(
        "Event Generator",
        "event-generator",
        "count"    -> "1".spel,
        "value"    -> "1".spel,
        "schedule" -> "T(java.time.Duration).parse('PT1M')".spel,
      )
      .emptySink("end", "dead-end")

  "The endpoint for live data should" - {
    "return present, but empty live data" in {
      val mockedResults =
        LiveData(TestResults[Json](Map.empty, Map.empty, Map.empty, Map.empty, List.empty), Map.empty)
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(
            new LiveDataPreviewSupported {
              override def getLiveData(
                  processIdWithName: ProcessIdWithName
              ): Future[Either[LiveDataError, LiveData]] = Future.successful(Right(mockedResults))
            }
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.OK.intValue)
        .equalsJsonBody(
          s"""{
             |  "results": {
             |      "nodeTransitionResults": [],
             |      "invocationResults": {},
             |      "externalInvocationResults": {},
             |      "exceptions": []
             |  },
             |  "counts": {
             |      "Event Generator": {
             |          "all": 0,
             |          "errors": 0,
             |          "fragmentCounts": {}
             |      },
             |      "end": {
             |          "all": 0,
             |          "errors": 0,
             |          "fragmentCounts": {}
             |      }
             |  },
             |  "nodeTransitionThroughput": []
             |}""".stripMargin
        )
    }
    "return not present live data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(
            new LiveDataPreviewSupported {
              override def getLiveData(
                  processIdWithName: ProcessIdWithName
              ): Future[Either[LiveDataError, LiveData]] =
                Future.successful(Left(LiveDataError.NoLiveDataAvailableForScenario))
            }
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.NoContent.intValue)
    }
    "return live data not supported error" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureLiveDataPreviewSupport(NoLiveDataPreviewSupport)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/liveData/${exampleScenario.name}")
        .Then()
        .statusCode(StatusCodes.NotImplemented.intValue)
    }
  }

}
