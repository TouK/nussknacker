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
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.{Category1, Category2}
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuTestScenarioManager, WithMockableDeploymentManager}

class AppApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The app health check endpoint should" - {
    "return simple designer health check (not checking scenario statuses) without authentication" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"), category = Category1)

          MockableDeploymentManager.configure(
            Map("id1" -> SimpleStateStatus.Running)
          )
        }
        .when()
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
      "return health check also if cannot retrieve statuses" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
             |  "status": "ERROR",
             |  "message": "Scenarios with status PROBLEM",
             |  "processes": [ "id1", "id3" ]
             |}""".stripMargin
          )
      }
      "not return health check when scenario is canceled" in {
        given()
          .applicationState {
            createDeployedCanceledExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)

            MockableDeploymentManager.configure(
              Map("id2" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"))
            )
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(500)
          .equalsJsonBody(
            s"""{
               |  "status": "ERROR",
               |  "message": "Scenarios with status PROBLEM",
               |  "processes": [ "id2" ]
               |}""".stripMargin
          )
      }
      "return health check ok if statuses are ok" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> SimpleStateStatus.Running,
                "id2" -> SimpleStateStatus.Running,
              )
            )
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
             |  "status": "OK",
             |  "message": null,
             |  "processes": null
             |}""".stripMargin
          )
      }
      "not report deployment in progress as fail" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)

            MockableDeploymentManager.configure(
              Map("id1" -> SimpleStateStatus.Running)
            )
          }
          .when()
          .basicAuth("reader", "reader")
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
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
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials pass should" - {
      "authenticate as anonymous" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category2)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "status": "OK",
               |    "message": null,
               |    "processes": null
               |}""".stripMargin
          )
      }
    }
  }

  "The scenario validation health check endpoint when" - {
    "authenticated should" - {
      "return ERROR status and list of scenarios with validation errors" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)

            MockableDeploymentManager.configure(
              Map("id1" -> SimpleStateStatus.Running)
            )
          }
          .basicAuth("reader", "reader")
          .when()
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
      "return OK status and empty list of scenarios where there are no validation errors" in {
        given()
          .applicationState {}
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(
              s"""{
               |  "status": "OK",
               |  "message": null,
               |  "processes": null
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
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The app build info endpoint should" - {
    "return build info without authentication" in {
      given()
        .when()
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
             |    "streaming": {
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

  "The app server config info endpoint should" - {
    "return config when" - {
      "user is an admin" in {
        given()
          .basicAuth("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(200)
          .body(is(not(emptyString())))
      }
    }
    "not return config when" - {
      "user is an admin" in {
        given()
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
      }
      "there is no authentication provided" in {
        given()
          .when()
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
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(
              s"""{
               |  "Category1": "streaming",
               |  "Category2": "streaming2"
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
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

  "The processing type data reload endpoint when" - {
    "authenticated should" - {
      "allow to reload" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .basicAuth("admin", "admin")
          .when()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(204)
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "not authorized as admin should" - {
      "forbid access" in {
        given()
          .applicationState {
            createDeployedExampleScenario(ProcessName("id1"), category = Category1)
            createDeployedExampleScenario(ProcessName("id2"), category = Category1)
            createDeployedExampleScenario(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                "id1" -> ProblemStateStatus.FailedToGet,
                "id2" -> SimpleStateStatus.Running,
                "id3" -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .basicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(403)
          .body(equalTo("The supplied authentication is not authorized to access this resource"))
      }
    }
  }

  override def nuTestConfig: Config = super.nuTestConfig
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
