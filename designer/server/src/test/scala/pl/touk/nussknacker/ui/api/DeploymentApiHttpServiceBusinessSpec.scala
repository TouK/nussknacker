package pl.touk.nussknacker.ui.api

import com.nimbusds.jose.util.StandardCharset
import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.commons.io.FileUtils
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.test.docker.FileSystemBind
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.{NuItTest, WithBatchConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBatchDesignerConfig,
  WithBusinessCaseRestAssuredUsersExtensions,
  WithFlinkContainersDeploymentManager
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging, VeryPatientScalaFutures}

import java.nio.file.Files

class DeploymentApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithBatchDesignerConfig
    with WithFlinkContainersDeploymentManager
    with WithBatchConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with LazyLogging
    with VeryPatientScalaFutures
    with Matchers {

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions_summary-")

  private lazy val tablesDefinitionBind = FileSystemBind(
    "designer/server/src/test/resources/config/business-cases/tables-definition.sql",
    "/opt/flink/designer/server/src/test/resources/config/business-cases/tables-definition.sql",
    BindMode.READ_ONLY
  )

  private lazy val inputTransactionsBind = FileSystemBind(
    "designer/server/src/test/resources/transactions",
    "/transactions",
    BindMode.READ_ONLY
  )

  private lazy val outputTransactionsSummaryBind = FileSystemBind(
    outputDirectory.toString,
    "/output/transactions_summary",
    BindMode.READ_WRITE
  )

  override protected def jobManagerExtraFSBinds: List[FileSystemBind] =
    List(
      tablesDefinitionBind,
      // input must be also available on the JM side to allow their to split work into multiple subtasks
      inputTransactionsBind
    )

  override protected def taskManagerExtraFSBinds: List[FileSystemBind] = {
    List(
      // table definitions must be also on the TM side. This is necessary because we create full model definition for the purpose of
      // interpreter used in scenario parts using built-in components - TODO: it shouldn't be needed
      tablesDefinitionBind,
      inputTransactionsBind,
      outputTransactionsSummaryBind
    )
  }

  private val scenarioName = "batch-test"

  private val sourceNodeId = "fooSourceNodeId"

  private val scenario = ScenarioBuilder
    .streaming(scenarioName)
    .source(sourceNodeId, "table", "Table" -> Expression.spel("'transactions'"))
    .emptySink(
      "sink",
      "table",
      "Table" -> Expression.spel("'transactions_summary'"),
      "Value" -> Expression.spel("#input")
    )

  private val correctDeploymentRequest = s"""{
                                            |  "nodesDeploymentData": {
                                            |    "$sourceNodeId": "`date` = '2024-01-01'"
                                            |  }
                                            |}""".stripMargin

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(outputDirectory.toFile) // it might not work because docker user can has other uid
    super.afterAll()
  }

  "The endpoint for deployment requesting" - {
    "authenticated as user with deploy access should" - {
      "run deployment" in {
        val requestedDeploymentId = "some-requested-deployment-id"
        given()
          .applicationState {
            createSavedScenario(scenario)
          }
          .when()
          .basicAuthAdmin()
          .jsonBody(correctDeploymentRequest)
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/$requestedDeploymentId")
          .Then()
          // TODO (next PRs): we should return 201 and we should check status of deployment before we verify output
          .statusCode(200)
          .verifyExternalState {
            outputTransactionSummaryContainsResult()
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
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/foo-deployment-id")
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
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/foo-deployment-id")
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
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/foo-deployment-id")
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
          .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/foo-deployment-id")
          .Then()
          .statusCode(403)
      }
    }
  }

  private def outputTransactionSummaryContainsResult(): Unit = {
    // finished deploy doesn't mean that processing is finished
    // TODO (next PRs): we need to wait for the job completed status instead
    val transactionSummaryDirectories = eventually {
      val directories = Option(outputDirectory.toFile.listFiles()).toList.flatten
      directories should have size 1
      directories
    }
    transactionSummaryDirectories should have size 1
    val matchingPartitionDirectory = transactionSummaryDirectories.head
    matchingPartitionDirectory.getName shouldEqual "date=2024-01-01"

    val partitionFiles = Option(matchingPartitionDirectory.listFiles()).toList.flatten
    partitionFiles should have size 1
    val firstFile = partitionFiles.head

    val content =
      FileUtils.readFileToString(firstFile, StandardCharset.UTF_8)

    // TODO (next PRs): aggregate by clientId
    content should include(
      """"2024-01-01 10:00:00",client1,1.12
        |"2024-01-01 10:01:00",client2,2.21
        |"2024-01-01 10:02:00",client1,3""".stripMargin
    )
  }

}
