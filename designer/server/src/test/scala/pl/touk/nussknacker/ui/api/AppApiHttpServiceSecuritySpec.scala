package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers._
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, ProblemDeploymentStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}

import java.time.Instant

class AppApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The app health check endpoint should" - {
    "require no auth to return simple health check (scenario statuses)" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"), category = Category1)
          createDeployedExampleScenario(ProcessName("id2"), category = Category2)

          MockableDeploymentManager.configureScenarioStatuses(
            Map(
              "id1" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now())
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
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
    "no credentials were passed should" - {
      "authenticate as anonymous and show all processes related to anonymous role category" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category2)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Problem.Failed,
                "id3" -> DeploymentStatus.Problem.Failed
              )
            )
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
               |  "status": "ERROR",
               |  "message": "Scenarios with status PROBLEM",
               |  "processes": [ "id2", "id3" ]
               |}""".stripMargin
          )
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map.empty
            )
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
               |  "status": "ERROR",
               |  "message": "Scenarios with validation errors",
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
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
    "no credentials were passed should" - {
      "authenticate as anonymous and show all with validation errors related to anonymous role category" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category2)
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
               |  "status": "ERROR",
               |  "message": "Scenarios with validation errors",
               |  "processes": [ "id2" ]
               |}""".stripMargin
          )
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
               |  "globalBuildInfo": null
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
    "no credentials were passed should" - {
      "authenticate as anonymous and forbid access" in {
        given()
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "Category1": "streaming1"
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
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
    "no credentials were passed should" - {
      "authenticate as anonymous and show all categories and processing related to anonymous role category" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> DeploymentStatus.Problem.Failed,
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "Category2": "streaming2"
               |}""".stripMargin
          )
      }
    }
  }

  "The model reload endpoint when" - {
    "authenticated should" - {
      "allow to reload when user is an admin" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> ProblemDeploymentStatus(s"Failed to get a state of the scenario."),
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .basicAuthAdmin()
          .post(s"$nuDesignerHttpAddress/api/app/model/reload")
          .Then()
          .statusCode(204)
      }
      "not allow to reload when user is not an admin" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> ProblemDeploymentStatus(s"Failed to get a state of the scenario."),
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .basicAuthAllPermUser()
          .post(s"$nuDesignerHttpAddress/api/app/model/reload")
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

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> ProblemDeploymentStatus("Failed to get a state of the scenario."),
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .basicAuthUnknownUser()
          .post(s"$nuDesignerHttpAddress/api/app/model/reload")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category2)

            MockableDeploymentManager.configureScenarioStatuses(
              Map(
                "id1" -> ProblemDeploymentStatus("Failed to get a state of the scenario."),
                "id2" -> DeploymentStatus.Running(VersionId(1), Instant.now()),
              )
            )
          }
          .when()
          .noAuth()
          .post(s"$nuDesignerHttpAddress/api/app/model/reload")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
      }
    }
  }

  override def designerRawConfig: Config = super.designerRawConfig
    .withValue("enableConfigEndpoint", fromAnyRef(true))

}
