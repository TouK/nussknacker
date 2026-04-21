package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.StrictLogging
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.{anyOf, equalTo}
import org.scalatest.{LoneElement, Suite}
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.testcontainers.containers.BindMode
import org.testcontainers.images.builder.Transferable
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
import pl.touk.nussknacker.test.containers.ContainerVolume

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import scala.jdk.CollectionConverters._

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

  protected def inputTransactionsFiles: Map[String, String]

  private val inputContainerPath  = "/transactions"
  private val outputContainerPath = "/output/transactions_summary"

  private val volumeNameSuffix = s"${getClass.getSimpleName}-${UUID.randomUUID()}"
  private val inputVolumeName  = s"nussknacker-test-transactions-$volumeNameSuffix"
  private val outputVolumeName = s"nussknacker-test-transactions-summary-$volumeNameSuffix"

  private val sharedVolumeMounts: List[ContainerVolume] = List(
    ContainerVolume(inputVolumeName, inputContainerPath, BindMode.READ_WRITE),
    ContainerVolume(outputVolumeName, outputContainerPath, BindMode.READ_WRITE)
  )

  override protected def jobManagerExtraVolumes: List[ContainerVolume]  = sharedVolumeMounts
  override protected def taskManagerExtraVolumes: List[ContainerVolume] = sharedVolumeMounts

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    seedInputTransactionsFiles()
  }

  private def seedInputTransactionsFiles(): Unit = {
    inputTransactionsFiles.foreach { case (relativePath, content) =>
      val targetPath  = s"$inputContainerPath/$relativePath"
      val parentPath  = targetPath.substring(0, targetPath.lastIndexOf('/'))
      val mkdirResult = jobManagerContainer.container.execInContainer("mkdir", "-p", parentPath)
      if (mkdirResult.getExitCode != 0) {
        throw new IllegalStateException(
          s"Failed to create directory $parentPath in JobManager container: ${mkdirResult.getStderr}"
        )
      }
      jobManagerContainer.container.copyFileToContainer(
        Transferable.of(content.getBytes(StandardCharsets.UTF_8)),
        targetPath
      )
    }
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

  protected def readLinesFromLoneOutputTransactionsSummaryPartition(expectedLonePartitionName: String): List[String] = {
    val partitionName = listJobManagerFiles(outputContainerPath).loneElement
    partitionName shouldEqual expectedLonePartitionName

    val partitionPath     = s"$outputContainerPath/$partitionName"
    val fileName          = listJobManagerFiles(partitionPath).loneElement
    val containerFilePath = s"$partitionPath/$fileName"

    val localTempFile = Files.createTempFile(s"nussknacker-${getClass.getSimpleName}-output-", ".csv")
    try {
      jobManagerContainer.container.copyFileFromContainer(containerFilePath, localTempFile.toString)
      Files.readAllLines(localTempFile, StandardCharsets.UTF_8).asScala.toList
    } finally {
      Files.deleteIfExists(localTempFile)
    }
  }

}
