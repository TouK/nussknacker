package pl.touk.nussknacker.ui.api

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers._
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}
import pl.touk.nussknacker.tests.base.it.{NuItTest, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.tests.config.WithRichDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.tests.config.{
  WithMockableDeploymentManager,
  WithRichConfigRestAssuredUsersExtensions,
  WithRichDesignerConfig
}

class AppApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The app health check endpoint should" - {
    "require no auth to return simple health check (scenario statuses)" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"), category = Category1)
          createDeployedExampleScenario(ProcessName("id2"), category = Category2)

          MockableDeploymentManager.configure(
            Map(
              "id1" -> SimpleStateStatus.Running,
              "id2" -> SimpleStateStatus.Running
            )
          )
        }
        .when()
        .noAuth()
        .get(s"$nuDesignerHttpAddress/api/app/healthCheck")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "status":"OK",
             |  "processes":null,
             |  "message":null
             |}""".stripMargin
        )
    }
  }

  "The scenario deployment health check endpoint when" - {
    "authenticated should" - {
      "return health check for scenarios from allowed categories for the given user" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
               |  "status": "ERROR",
               |  "message": "Scenarios with status PROBLEM",
               |  "processes": [ "id1" ]
               |}""".stripMargin
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The scenario validation health check endpoint when" - {
    "authenticated should" - {
      "return ERROR statuses for scenarios from allowed categories for the given user" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> SimpleStateStatus.Running,
                "id2" -> SimpleStateStatus.Running
              )
            )
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(500)
          .body(
            equalsJson(
              s"""{
                 |  "status": "ERROR",
                 |  "message": "Scenarios with validation errors",
                 |  "processes": [ "id1" ]
                 |}""".stripMargin
            )
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The app build info endpoint should" - {
    "require no auth to return build info" in {
      given()
        .when()
        .noAuth()
        .get(s"$nuDesignerHttpAddress/api/app/buildInfo")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""{
               |  "name": "nussknacker-common-api",
               |  "gitCommit": "^\\\\w{40}$$",
               |  "buildTime": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}(?::\\\\d{2}\\\\.(?:\\\\d{9}|\\\\d{6}|\\\\d{3})|:\\\\d{2}|)$$",
               |  "version": "^\\\\d+\\\\.\\\\d+\\\\.\\\\d+(?:-.+)*$$",
               |  "processingType": {
               |    "streaming2": {
               |      "process-version": "0.1",
               |      "engine-version": "0.1",
               |      "generation-time": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}(?::\\\\d{2}\\\\.(?:\\\\d{9}|\\\\d{6}|\\\\d{3})|:\\\\d{2}|)$$"
               |    },
               |    "streaming1": {
               |      "process-version": "0.1",
               |      "engine-version": "0.1",
               |      "generation-time": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}(?::\\\\d{2}\\\\.(?:\\\\d{9}|\\\\d{6}|\\\\d{3})|:\\\\d{2}|)$$"
               |    }
               |  },
               |  "globalBuildInfo": {
               |    "build-config-1": "1",
               |    "build-config-2": "2"
               |  },
               |  "build-config-1": "1",
               |  "build-config-2": "2"
               |}""".stripMargin
          )
        )
    }
  }

  "The app server config info endpoint when" - {
    "authenticated should" - {
      "return config when user is an admin" in {
        given()
          .when()
          .basicAuthAdmin()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(200)
          .body(is(not(emptyString())))
      }
      "return FORBIDDEN when user is not a admin" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The user's categories and processing types info endpoint when" - {
    "authenticated should" - {
      "return user's categories and processing types" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(
              s"""{
                 |  "Category1": "streaming1"
                 |}""".stripMargin
            )
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The processing type data reload endpoint when" - {
    "authenticated should" - {
      "allow to reload when user is an admin" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthAdmin()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(204)
      }
      "not allow to reload when user is not an admin" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthAllPermUser()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L)),
              )
            )
          }
          .when()
          .basicAuthUnknownUser()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  override def designerConfig: Config = super.designerConfig
    .withValue("enableConfigEndpoint", fromAnyRef(true))
    .withValue(
      "globalBuildInfo",
      ConfigFactory
        .empty()
        .withValue("build-config-1", fromAnyRef("1"))
        .withValue("build-config-2", fromAnyRef("2"))
        .root()
    )

}
