package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.deployment.NewDeploymentId

class DeploymentApiHttpServiceDeploymentCommentSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with BaseDeploymentApiHttpServiceBusinessSpec
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with LazyLogging
    with VeryPatientScalaFutures
    with Matchers {

  private val configuredPhrase = "foo"

  override def designerConfig: Config = {
    super.designerConfig
      .withValue("deploymentCommentSettings.validationPattern", ConfigValueFactory.fromAnyRef(s".*$configuredPhrase.*"))
  }

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
            .put(s"$nuDesignerHttpAddress/api/deployments/${NewDeploymentId.generate}")
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
            .put(s"$nuDesignerHttpAddress/api/deployments/${NewDeploymentId.generate}")
            .Then()
            .statusCode(400)
        }
      }

      "When matching deployment comment is passed should" - {
        "return accepted status code and run deployment that will process input files" in {
          val requestedDeploymentId = NewDeploymentId.generate
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
              waitForDeploymentStatusMatches(requestedDeploymentId, SimpleStateStatus.Finished)
            }
            .verifyExternalState {
              outputTransactionSummaryContainsExpectedResult()
            }
        }
      }
    }
  }

}
