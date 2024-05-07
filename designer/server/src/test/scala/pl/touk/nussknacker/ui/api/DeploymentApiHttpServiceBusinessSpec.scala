package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.commons.io.FileUtils
import org.scalatest.LoneElement
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging, VeryPatientScalaFutures}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentId

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.jdk.CollectionConverters._

class DeploymentApiHttpServiceBusinessSpec
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
    with Matchers
    with LoneElement {

  override protected def populateInputTransactionsDirectory(rootDirectory: Path): Unit = {
    val firstPartition = rootDirectory.resolve("date=2024-01-01")
    firstPartition.toFile.mkdir()
    FileUtils.write(
      firstPartition.resolve("transaction-1.csv").toFile,
      """"2024-01-01 10:00:00",client1,1
        |"2024-01-01 10:01:00",client2,2
        |"2024-01-01 10:02:00",client1,3
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    val secondPartition = rootDirectory.resolve("date=2024-01-02")
    secondPartition.toFile.mkdir()
    FileUtils.write(
      secondPartition.resolve("transaction-1.csv").toFile,
      """"2024-01-02 10:00:00",client1,1
        |"2024-01-02 10:01:00",client2,2
        |"2024-01-02 10:02:00",client1,3
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private val correctDeploymentRequest = s"""{
                                            |  "scenarioName": "$scenarioName",
                                            |  "nodesDeploymentData": {
                                            |    "$sourceNodeId": "`date` = '2024-01-01'"
                                            |  }
                                            |}""".stripMargin

  "The deployment requesting endpoint" - {
    "authenticated as user with deploy access" - {
      "when invoked once should" - {
        "return accepted status code and run deployment that will process input files" in {
          val requestedDeploymentId = DeploymentId.generate
          given()
            .applicationState {
              createSavedScenario(scenario)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(correctDeploymentRequest)
            .put(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId")
            .Then()
            .statusCode(202)
            .verifyApplicationState {
              waitForDeploymentStatusMatches(requestedDeploymentId, SimpleStateStatus.Finished)
            }
            .verifyExternalState {
              val resultFile = getLoneFileFromLoneOutputTransactionsSummaryPartitionWithGivenName("date=2024-01-01")
              FileUtils.readLines(resultFile, StandardCharsets.UTF_8).asScala.toSet shouldBe Set(
                "client1,4",
                "client2,2"
              )
            }
        }
      }

      "when invoked twice with the same deployment id should" - {
        "return conflict status code" in {
          val requestedDeploymentId = DeploymentId.generate
          given()
            .applicationState {
              createSavedScenario(scenario)
              runDeployment(requestedDeploymentId)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(correctDeploymentRequest)
            .put(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId")
            .Then()
            // TODO: idempotence (return 2xx when previous body is the same as requested)
            .statusCode(409)
        }
      }

      "when invoked twice with different deployment id should" - {
        "return status of correct deployment" in {
          val firstDeploymentId  = DeploymentId.generate
          val secondDeploymentId = DeploymentId.generate
          `given`()
            .applicationState {
              createSavedScenario(scenario)
              runDeployment(firstDeploymentId)
              waitForDeploymentStatusMatches(firstDeploymentId, SimpleStateStatus.Finished)
            }
            .when()
            .basicAuthAdmin()
            .jsonBody(correctDeploymentRequest)
            .put(s"$nuDesignerHttpAddress/api/deployments/$secondDeploymentId")
            .Then()
            .statusCode(202)
            .verifyApplicationState {
              checkDeploymentStatusMatches(firstDeploymentId, SimpleStateStatus.Finished)
              checkDeploymentStatusMatches(
                secondDeploymentId,
                SimpleStateStatus.DuringDeploy,
                SimpleStateStatus.Running
              )
            }
        }
      }
    }

    "not authenticated should" - {
      "return unauthenticated status code" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
          .Then()
          .statusCode(401)
      }
    }

    "badly authenticated should" - {
      "return unauthenticated status code" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthUnknownUser()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
          .Then()
          .statusCode(401)
      }
    }

    "authenticated without read access to category should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthNoPermUser()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
          .Then()
          .statusCode(403)
      }
    }

    "authenticated without deploy access to category should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthWriter()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/deployments/${DeploymentId.generate}")
          .Then()
          .statusCode(403)
      }
    }
  }

  private def runDeployment(requestedDeploymentId: DeploymentId): Unit = {
    given()
      .when()
      .basicAuthAdmin()
      .jsonBody(correctDeploymentRequest)
      .put(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId")
      .Then()
      .statusCode(202)
  }

}
