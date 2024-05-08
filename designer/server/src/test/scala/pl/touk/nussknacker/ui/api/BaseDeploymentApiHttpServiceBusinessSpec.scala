package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.commons.io.FileUtils
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{LoneElement, Suite}
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.docker.FileSystemBind
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithFlinkContainersDeploymentManager
}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentId

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

trait BaseDeploymentApiHttpServiceBusinessSpec extends WithFlinkContainersDeploymentManager {
  self: NuItTest
    with Suite
    with LazyLogging
    with Matchers
    with Eventually
    with LoneElement
    with WithBusinessCaseRestAssuredUsersExtensions =>

  protected val scenarioName = "batch-test"

  protected val sourceNodeId = "fooSourceNodeId"

  protected val scenario: CanonicalProcess = ScenarioBuilder
    .streaming(scenarioName)
    .source(sourceNodeId, "table", "Table" -> Expression.spel("'transactions'"))
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
      "Table" -> Expression.spel("'transactions_summary'"),
      "Value" -> Expression.spel(
        "{client_id: #keyValues[0], date: #keyValues[1], amount: #agg}"
      )
    )

  private lazy val inputDirectory = {
    val rootDirectory = Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions-")
    Files.setPosixFilePermissions(rootDirectory, PosixFilePermissions.fromString("rwxr-xr-x"))
    populateInputTransactionsDirectory(rootDirectory)
    rootDirectory
  }

  protected def populateInputTransactionsDirectory(rootDirectory: Path): Unit = {
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

  protected def waitForDeploymentStatusMatches(
      requestedDeploymentId: DeploymentId,
      expectedStatus: StateStatus
  ): Unit = {
    // A little bit longer interval than default to avoid too many log entries of requests
    eventually(Interval(Span(2, Seconds))) {
      given()
        .when()
        .basicAuthAdmin()
        .get(s"$nuDesignerHttpAddress/api/deployments/$requestedDeploymentId/status")
        .Then()
        .statusCode(200)
        .equalsPlainBody(expectedStatus.name)
    }
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
