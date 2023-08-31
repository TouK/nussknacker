package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers._
import org.scalatest.funsuite.AnyFunSuiteLike
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
    with AnyFunSuiteLike
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

  test("it should return health check also if cannot retrieve statuses") {
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

  test("it shouldn't return healthcheck when scenario canceled") {
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

  test("it should return healthcheck ok if statuses are ok") {
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

  test("it should not report deployment in progress as fail") {
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

  test("it should return config") {
    given()
      .auth().basic("admin", "admin")
      .when()
      .get(s"$nuDesignerHttpAddress/api/app/config")
      .Then()
      .statusCode(200)
      .body(is(not(emptyString())))
  }

  test("it shouldn't return config when the user is not admin") {
    given()
      .auth().basic("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/app/config")
      .Then()
      .statusCode(403)
      .body(equalTo("The supplied authentication is not authorized to access this resource"))
  }

  test("it should return build info without authentication") {
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

  test("it should should return simple designer health check (not checking scenario statuses) without authentication") {
    given()
      .applicationConfiguration {
        createDeployedProcess(ProcessName("id1"))
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
