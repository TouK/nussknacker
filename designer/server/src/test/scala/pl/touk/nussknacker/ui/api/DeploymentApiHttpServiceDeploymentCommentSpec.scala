package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.StrictLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.LoneElement
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatusName
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.test.{
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails,
  VeryPatientScalaFutures
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}

class DeploymentApiHttpServiceDeploymentCommentSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with BaseDeploymentApiHttpServiceBusinessSpec
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with StrictLogging
    with VeryPatientScalaFutures
    with Matchers
    with LoneElement {

  private val configuredPhrase = "foo"

  override def designerRawConfig: Config = {
    super.designerRawConfig
      .withValue("deploymentCommentSettings.validationPattern", ConfigValueFactory.fromAnyRef(s".*$configuredPhrase.*"))
  }

  override protected def inputTransactionsFiles: Map[String, String] = Map(
    // first partition
    "date=2024-01-01/transaction-1.csv" ->
      """"2024-01-01 10:00:00",client1,1
        |"2024-01-01 10:01:00",client2,2
        |"2024-01-01 10:02:00",client1,3
        |""".stripMargin,
    // second partition
    "date=2024-01-02/transaction-1.csv" ->
      """"2024-01-02 10:00:00",client1,1
        |"2024-01-02 10:01:00",client2,2
        |"2024-01-02 10:02:00",client1,3
        |""".stripMargin,
  )

  "The deployment requesting endpoint" - {
    "With validationPattern configured in deploymentCommentSettings" - {
      "When no deployment comment is passed should" - {
        "return 400" in {
          given()
            .applicationState {
              createSavedScenario(scenario)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(s"""{
                         |  "scenarioName": "$scenarioName",
                         |  "nodesDeploymentData": {
                         |    "$sourceNodeId": "`date` = '2024-01-01'"
                         |  }
                         |}""".stripMargin)
            .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
            .Then()
            .statusCode(400)
        }
      }

      "When mismatch deployment comment is passed should" - {
        "return 400" in {
          given()
            .applicationState {
              createSavedScenario(scenario)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(s"""{
                         |  "scenarioName": "$scenarioName",
                         |  "nodesDeploymentData": {
                         |    "$sourceNodeId": "`date` = '2024-01-01'"
                         |  },
                         |  "comment": "deployment comment not matching configured pattern"
                         |}""".stripMargin)
            .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
            .Then()
            .statusCode(400)
        }
      }

      "When matching deployment comment is passed should" - {
        "return accepted status code and run deployment that will process input files" in {
          val requestedDeploymentId = DeploymentId.generate
          given()
            .applicationState {
              createSavedScenario(scenario)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(s"""{
                         |  "scenarioName": "$scenarioName",
                         |  "nodesDeploymentData": {
                         |    "$sourceNodeId": "`date` = '2024-01-01'"
                         |  },
                         |  "comment": "comment with $configuredPhrase"
                         |}""".stripMargin)
            .put(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId")
            .Then()
            .statusCode(202)
            .verifyApplicationState {
              waitForDeploymentStatusNameMatches(requestedDeploymentId, DeploymentStatusName.finishedStatusName)
            }
            .verifyExternalState {
              readLinesFromLoneOutputTransactionsSummaryPartition("date=2024-01-01").toSet shouldBe Set(
                "client1,4",
                "client2,2"
              )
            }
        }
      }
    }
  }

}
