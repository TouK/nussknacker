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
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

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
    with PatientScalaFutures
    with Matchers {

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions_summary")

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

  "The endpoint for deployment requesting should" - {
    "run deployment" in {
      val scenarioName = "batch-test"
      val scenario = ScenarioBuilder
        .streaming(scenarioName)
        .source("source", "table", "Table" -> Expression.spel("'transactions'"))
        .emptySink(
          "sink",
          "table",
          "Table" -> Expression.spel("'transactions_summary'"),
          "Value" -> Expression.spel("#input")
        )
      val requestedDeploymentId = "some-requested-deployment-id"

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAdmin()
        .jsonBody("{}")
        .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/$requestedDeploymentId")
        .Then()
        // TODO: we should return 201 and we should check status of deployment before we verify output
        .statusCode(200)
        .verifyExternalState {
          outputTransactionSummaryContainsResult()
        }
    }
  }

  private def outputTransactionSummaryContainsResult(): Unit = {
    val transactionSummaryFiles = Option(outputDirectory.toFile.listFiles()).toList.flatten
    transactionSummaryFiles should have size 1
    val transactionsSummaryContent =
      FileUtils.readFileToString(transactionSummaryFiles.head, StandardCharset.UTF_8)
    // TODO: aggregate by clientId
    transactionsSummaryContent should include(
      """client1,1.12
        |client2,2.21
        |client1,3""".stripMargin
    )
  }

}
