package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.StrictLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.commons.io.FileUtils
import org.hamcrest.Matchers.{anyOf, equalTo}
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{LoneElement, Suite}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatusName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithFlinkContainersDeploymentManager
}
import pl.touk.nussknacker.test.containers.FileSystemBind

import java.io.File
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

trait BaseDeploymentApiHttpServiceBusinessSpec extends WithFlinkContainersDeploymentManager {
  self: NuItTest
    with Suite
    with StrictLogging
    with Matchers
    with Eventually
    with LoneElement
    with WithBusinessCaseRestAssuredUsersExtensions =>

  protected val scenarioName = "batch-test"

  protected val sourceNodeId = "fooSourceNodeId"

  protected val scenario: CanonicalProcess = ScenarioBuilder
    .streaming(scenarioName)
    .source(sourceNodeId, "table", "Table" -> Expression.spel("'`default_catalog`.`default_database`.`transactions`'"))
    .customNode(
      id = "aggregate",
      outputVar = "agg",
      customNodeRef = "aggregate",
      "groupBy"     -> Expression.spel("#input.client_id + ',' + #input.date"),
      "aggregateBy" -> Expression.spel("#input.amount"),
      "aggregator"  -> Expression.spel("'Sum'"),
    )
    // TODO: get rid of concatenating the key and pass the timedate to output table
    .buildSimpleVariable(
      "var",
      "keyValues",
      Expression.spel("#key.split(',')")
    )
    .emptySink(
      id = "sink",
      typ = "table",
      "Table"      -> Expression.spel("'`default_catalog`.`default_database`.`transactions_summary`'"),
      "Raw editor" -> Expression.spel("false"),
      "client_id"  -> Expression.spel("#keyValues[0]"),
      "date"       -> Expression.spel("#keyValues[1]"),
      "amount"     -> Expression.spel("#agg"),
    )

  protected val fragment: CanonicalProcess = ScenarioBuilder
    .fragment(scenarioName)
    .fragmentOutput("out", "out")

  private lazy val inputDirectory = {
    val directory = Files.createTempDirectory(
      s"nusssknacker-${getClass.getSimpleName}-transactions-",
      // be default temp directory is read only for user
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"))
    )
    populateInputTransactionsDirectory(directory)
    directory
  }

  protected def populateInputTransactionsDirectory(rootDirectory: Path): Unit

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions_summary-")

  // TODO: use DECIMAL(15, 2) type instead of INT after adding support for all primitive types
  private lazy val tablesDefinitionBind = FileSystemBind(
    "designer/server/src/test/resources/config/business-cases/tables-definition.sql",
    "/opt/flink/designer/server/src/test/resources/config/business-cases/tables-definition.sql",
    BindMode.READ_ONLY
  )

  private lazy val inputTransactionsBind = FileSystemBind(
    inputDirectory.toString,
    "/transactions",
    BindMode.READ_WRITE
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
      inputTransactionsBind,
      // output directory has to be available on JM to allow writing the final output file and deleting the temp files
      outputTransactionsSummaryBind
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

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(inputDirectory.toFile)
    FileUtils.deleteQuietly(outputDirectory.toFile) // it might not work because docker user can has other uid
    super.afterAll()
  }

  protected def waitForDeploymentStatusNameMatches(
      requestedDeploymentId: DeploymentId,
      expectedStatusName: DeploymentStatusName
  ): Unit = {

    withClue(s"Deployment: $requestedDeploymentId") {
      eventually(Timeout(Span(60, Seconds)), Interval(Span(3, Seconds))) {
        checkDeploymentStatusNameMatches(requestedDeploymentId, expectedStatusName)
      }
    }
  }

  protected def checkDeploymentStatusNameMatches(
      requestedDeploymentId: DeploymentId,
      expectedStatusNames: DeploymentStatusName*
  ): Unit = {
    given()
      .when()
      .basicAuthAdmin()
      .get(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId/status")
      .Then()
      .statusCode(200)
      .body("name", anyOf(expectedStatusNames.map(statusName => equalTo[String](statusName.value)): _*))
  }

  protected def getLoneFileFromLoneOutputTransactionsSummaryPartitionWithGivenName(
      expectedLonePartitionName: String
  ): File = {
    val transactionSummaryDirectories = Option(outputDirectory.toFile.listFiles(!_.isHidden)).toList.flatten
    val matchingPartitionDirectory    = transactionSummaryDirectories.loneElement
    matchingPartitionDirectory.getName shouldEqual expectedLonePartitionName

    val partitionFiles = Option(matchingPartitionDirectory.listFiles(!_.isHidden)).toList.flatten
    partitionFiles.loneElement
  }

}
