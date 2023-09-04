package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuScenarioConfigurationHelper, WithMockableDeploymentManager}

class AppResourcesSpec
  extends NuItTest
    with WithMockableDeploymentManager
    with AnyWordSpecLike
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  override def nuTestConfig: Config = super.nuTestConfig
    .withValue("enableConfigEndpoint", fromAnyRef(true))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    MockableDeploymentManager.clean()
  }

  "The app health check endpoint" should {
    "return simple designer health check (not checking scenario statuses) without authentication" in {
      given()
        .applicationConfiguration {
          createDeployedProcess(ProcessName("id1"), category = Category1)

          MockableDeploymentManager.configure(
            Map(
              ProcessName("id1") -> SimpleStateStatus.Running,
            )
          )
        }
        .when()
        .get(s"$nuDesignerHttpAddress/api/app/healthCheck")
        .Then()
        .statusCode(200)
        .body(equalsJson(
          s"""{
             |  "status":"OK",
             |  "processes":null,
             |  "message":null
             |}""".stripMargin
        ))
    }
  }

  "The app build info endpoint" should {
    "return build info without authentication" in {
      given()
        .when()
        .get(s"$nuDesignerHttpAddress/api/app/buildInfo")
        .Then()
        .statusCode(200)
        .body(matchJsonWithRegexValues(
          s"""{
             |  "name": "nussknacker-common-api",
             |  "gitCommit": "^\\\\w{40}$$",
             |  "buildTime": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{6}$$",
             |  "version": "1.12.0-SNAPSHOT",
             |  "processingType": {
             |    "streaming": {
             |      "process-version": "0.1",
             |      "engine-version": "0.1",
             |      "generation-time": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{6}$$"
             |    }
             |  }
             |}""".stripMargin
        ))
    }
  }

  "The app server config info endpoint" should {
    "return config" when {
      "user is an admin" in {
        given()
          .auth().basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config")
          .Then()
          .statusCode(200)
          .body(is(not(emptyString())))
      }
    }
    "not return config" when {
      "user is an admin" in {
        given()
          .auth().basic("reader", "reader")
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
          .statusCode(401)
          .body(equalTo("The resource requires authentication, which was not supplied with the request"))
      }
    }
  }

  "The user's categories and processing types info endpoint" when {
    "authenticated" should {
      "return user's categories and processing types" in {
        given()
          .applicationConfiguration {
            createDeployedProcess(ProcessName("id1"), category = Category1)
            createDeployedProcess(ProcessName("id2"), category = Category1)
            createDeployedProcess(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                ProcessName("id1") -> ProblemStateStatus.FailedToGet,
                ProcessName("id2") -> SimpleStateStatus.Running,
                ProcessName("id3") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .auth().basic("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(200)
          .body(equalsJson(
            s"""{
               |  "Category1": "streaming",
               |  "Category2": "streaming"
               |}""".stripMargin
          ))
      }
    }
    "not authenticated" should {
      "forbid access" in {
        given()
          .applicationConfiguration {
            createDeployedProcess(ProcessName("id1"), category = Category1)
            createDeployedProcess(ProcessName("id2"), category = Category1)
            createDeployedProcess(ProcessName("id3"), category = Category1)

            MockableDeploymentManager.configure(
              Map(
                ProcessName("id1") -> ProblemStateStatus.FailedToGet,
                ProcessName("id2") -> SimpleStateStatus.Running,
                ProcessName("id3") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
              )
            )
          }
          .when()
          .get(s"$nuDesignerHttpAddress/api/app/config/categoriesWithProcessingType")
          .Then()
          .statusCode(401)
          .body(equalTo("The resource requires authentication, which was not supplied with the request"))
      }
    }
  }

  "it should return health check also if cannot retrieve statuses" in {
    given()
      .applicationConfiguration {
        createDeployedProcess(ProcessName("id1"), category = Category1)
        createDeployedProcess(ProcessName("id2"), category = Category1)
        createDeployedProcess(ProcessName("id3"), category = Category1)

        MockableDeploymentManager.configure(
          Map(
            ProcessName("id1") -> ProblemStateStatus.FailedToGet,
            ProcessName("id2") -> SimpleStateStatus.Running,
            ProcessName("id3") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
          )
        )
      }
      .auth().basic("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
      .Then()
      .statusCode(500)
      .body(equalsJson(
        s"""{
           |  "status": "ERROR",
           |  "message": "Scenarios with status PROBLEM",
           |  "processes": [ "id1", "id3" ]
           |}""".stripMargin
      ))
  }

  "it shouldn't return healthcheck when scenario canceled" in {
    given()
      .applicationConfiguration {
        createDeployedCanceledProcess(ProcessName("id1"), category = Category1)
        createDeployedProcess(ProcessName("id2"), category = Category1)

        MockableDeploymentManager.configure(
          Map(
            ProcessName("id2") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
          )
        )
      }
      .auth().basic("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
      .Then()
      .statusCode(500)
      .body(equalsJson(
        s"""{
           |  "status": "ERROR",
           |  "message": "Scenarios with status PROBLEM",
           |  "processes": [ "id2" ]
           |}""".stripMargin
      ))
  }

  "it should return healthcheck ok if statuses are ok" in {
    given()
      .applicationConfiguration {
        createDeployedProcess(ProcessName("id1"), category = Category1)
        createDeployedProcess(ProcessName("id2"), category = Category1)

        MockableDeploymentManager.configure(
          Map(
            ProcessName("id1") -> SimpleStateStatus.Running,
            ProcessName("id2") -> SimpleStateStatus.Running,
          )
        )
      }
      .auth().basic("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
      .Then()
      .statusCode(200)
      .body(equalsJson(
        s"""{
           |  "status": "OK",
           |  "message": null,
           |  "processes": null
           |}""".stripMargin
      ))
  }

  "it should not report deployment in progress as fail" in {
    given()
      .applicationConfiguration {
        createDeployedProcess(ProcessName("id1"))
        createDeployedCanceledProcess(ProcessName("id1"))

        MockableDeploymentManager.configure(
          Map(
            ProcessName("id1") -> SimpleStateStatus.Running
          )
        )
      }
      .when()
      .auth().basic("reader", "reader")
      .get(s"$nuDesignerHttpAddress/api/app/healthCheck/process/deployment")
      .Then()
      .statusCode(200)
      .body(equalsJson(
        s"""{
           |  "status":"OK",
           |  "processes":null,
           |  "message":null
           |}""".stripMargin
      ))
  }

}
