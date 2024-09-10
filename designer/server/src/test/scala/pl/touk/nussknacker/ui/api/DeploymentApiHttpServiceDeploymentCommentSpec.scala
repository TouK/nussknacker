package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.commons.io.FileUtils
import org.scalatest.LoneElement
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBatchDesignerConfig, WithBusinessCaseRestAssuredUsersExtensions}
import pl.touk.nussknacker.test.{
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails,
  VeryPatientScalaFutures
}

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.jdk.CollectionConverters._

class DeploymentApiHttpServiceDeploymentCommentSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with BaseDeploymentApiHttpServiceBusinessSpec
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with LazyLogging
    with VeryPatientScalaFutures
    with Matchers
    with LoneElement {

  private val configuredPhrase = "foo"

  override def designerConfig: Config = {
    super.designerConfig
      .withValue("deploymentCommentSettings.validationPattern", ConfigValueFactory.fromAnyRef(s".*$configuredPhrase.*"))
  }

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

  testDbRef.db

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
              waitForDeploymentStatusNameMatches(requestedDeploymentId, DeploymentStatus.Finished.name)
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
    }
  }

}
