package pl.touk.nussknacker.ui.api

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers._
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers.be
import pl.touk.nussknacker.development.manager.BasicStatusDetails
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}

class AppApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with LazyLogging {

  private var simulatedChangeInApplicationConfig: Option[Config] = None

  "The app health check endpoint should" - {
    "return simple designer health check (with no scenario statuses check)" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map("id1" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))))
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

  "The scenario deployment health check endpoint should" - {
    "return health check also if cannot retrieve statuses" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"))
          createDeployedExampleScenario(ProcessName("id2"))
          createDeployedExampleScenario(ProcessName("id3"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map(
              "id1" -> BasicStatusDetails(ProblemStateStatus.Failed, None),
              "id2" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))),
            )
          )
        }
        .when()
        .basicAuthAllPermUser()
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
          createDeployedCanceledExampleScenario(ProcessName("id1"))
          createDeployedExampleScenario(ProcessName("id2"))
        }
        .when()
        .basicAuthAllPermUser()
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
          createDeployedExampleScenario(ProcessName("id1"))
          createDeployedExampleScenario(ProcessName("id2"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map(
              "id1" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))),
              "id2" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))),
            )
          )
        }
        .basicAuthAllPermUser()
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
          createDeployedExampleScenario(ProcessName("id1"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map("id1" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))))
          )
        }
        .when()
        .basicAuthAllPermUser()
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

  "The scenario validation health check endpoint should" - {
    "return ERROR status and list of scenarios with validation errors" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map("id1" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))))
          )
        }
        .basicAuthAllPermUser()
        .when()
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
    "return OK status and empty list of scenarios where there are no validation errors" in {
      given()
        .applicationState {}
        .basicAuthAllPermUser()
        .when()
        .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/validation")
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
  }

  "The app build info endpoint should" - {
    "return build info" in {
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
    "return config" in {
      given()
        .when()
        .basicAuthAdmin()
        .get(s"$nuDesignerHttpAddress/api/app/config")
        .Then()
        .statusCode(200)
        .body(is(not(emptyString())))
    }
  }

  "The user's categories and processing types info endpoint should" - {
    "return user's categories and processing types" in {
      given()
        .applicationState {
          createDeployedExampleScenario(ProcessName("id1"))
          createDeployedExampleScenario(ProcessName("id2"))
          createDeployedExampleScenario(ProcessName("id3"))

          MockableDeploymentManager.configureScenarioStatuses(
            Map(
              "id1" -> BasicStatusDetails(ProblemStateStatus.Failed, None),
              "id2" -> BasicStatusDetails(SimpleStateStatus.Running, Some(VersionId(1))),
            )
          )
        }
        .basicAuthAllPermUser()
        .when()
        .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "Category1": "streaming"
             |}""".stripMargin
        )
    }
  }

  "The processing type data reload endpoint should" - {
    "reload processing types-related model data when" - {
      "'scenarioTypes' configuration is changed" in {
        val componentNamesBeforeReload = fetchComponentGroupNamesWithOccurencesCount()

        given()
          .applicationState {
            simulatedChangeInApplicationConfig = Some(additionalProcessingTypeCustomization)
          }
          .when()
          .basicAuthAdmin()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(204)

        val componentNamesAfterReload = fetchComponentGroupNamesWithOccurencesCount()

        componentNamesAfterReload shouldNot be(componentNamesBeforeReload)
        componentNamesAfterReload("someComponentGroup") shouldBe 2
      }
    }
    "return error when" - {
      "scenario type is added" in {
        given()
          .applicationState {
            simulatedChangeInApplicationConfig = Some(
              ConfigFactory.parseString(
                s"""
                     |scenarioTypes {
                     |   streaming2 {
                     |     deploymentConfig {
                     |        type: "development-tests"
                     |     }
                     |     modelConfig {
                     |       classPath: []
                     |     }
                     |     category: "Default"
                     |   }
                     |}
                     |""".stripMargin
              )
            )
          }
          .when()
          .basicAuthAdmin()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(500)
          .body(
            startsWith("Processing types cannot be added, removed, or renamed during processing type reload.")
          )
      }
      "classpath of a model is changed" in {
        given()
          .applicationState {
            simulatedChangeInApplicationConfig = Some(
              ConfigFactory.parseString(
                s"""
                   |scenarioTypes {
                   |   streaming {
                   |     modelConfig {
                   |       classPath: ["changed.jar"]
                   |     }
                   |   }
                   |}
                   |""".stripMargin
              )
            )
          }
          .when()
          .basicAuthAdmin()
          .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
          .Then()
          .statusCode(500)
          .body(
            startsWith(
              "Error during processing types reload. Model ClassLoader dependencies such as classpath cannot be modified during reload."
            )
          )
      }
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    if (simulatedChangeInApplicationConfig.isDefined) {
      simulatedChangeInApplicationConfig = None
      forceReloadProcessingTypes()
    }
  }

  override def designerConfig: Config = {
    simulatedChangeInApplicationConfig match {
      case Some(customization) => customization.withFallback(originDesignerConfig)
      case None                => originDesignerConfig
    }
  }

  private def originDesignerConfig = {
    super.designerConfig
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

  private def additionalProcessingTypeCustomization = {
    ConfigFactory.parseString(
      s"""
         |scenarioTypes {
         |   streaming {
         |     modelConfig {
         |        componentsUiConfig {
         |          sendCommunication {
         |             componentGroup: "someComponentGroup"
         |          }
         |        }
         |     }
         |   }
         |}
         |""".stripMargin
    )
  }

  private def fetchComponentGroupNamesWithOccurencesCount(): Map[String, Int] = {
    val body = given()
      .when()
      .basicAuthAdmin()
      .get(s"$nuDesignerHttpAddress/api/components")
      .Then()
      .statusCode(200)
    body
      .extractList("componentGroupName")
      .groupBy(identity)
      .view
      .map { case (name, occurences) => name -> occurences.length }
      .toMap
  }

  private def forceReloadProcessingTypes(): Unit = {
    given()
      .when()
      .basicAuthAdmin()
      .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
      .Then()
      .statusCode(204)
  }

}
