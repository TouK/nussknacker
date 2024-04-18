package pl.touk.nussknacker.ui.api

import com.nimbusds.jose.util.StandardCharset
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.BindMode
import pl.touk.nussknacker.engine.flink.test.docker.FileSystemBind
import pl.touk.nussknacker.test.config.WithFlinkContainersDeploymentManager
import scala.jdk.CollectionConverters._

import java.nio.file.Files

trait BaseDeploymentApiHttpServiceBusinessSpec extends WithFlinkContainersDeploymentManager {
  self: Suite with LazyLogging with Matchers with Eventually =>

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions_summary-")

  // TODO: use DECIMAL(15, 2) type instead of INT after adding support for all primitive types
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
    FileUtils.deleteQuietly(outputDirectory.toFile) // it might not work because docker user can has other uid
    super.afterAll()
  }

  protected def outputTransactionSummaryContainsResult(): Unit = {
    // finished deploy doesn't mean that processing is finished
    // TODO (next PRs): we need to wait for the job completed status instead
    val transactionSummaryDirectories = eventually {
      val directories = Option(outputDirectory.toFile.listFiles().filter(!_.isHidden)).toList.flatten
      directories should have size 1
      directories
    }
    transactionSummaryDirectories should have size 1
    val matchingPartitionDirectory = transactionSummaryDirectories.head
    matchingPartitionDirectory.getName shouldEqual "date=2024-01-01"

    eventually {
      val partitionFiles = Option(matchingPartitionDirectory.listFiles().filter(!_.isHidden)).toList.flatten
      partitionFiles should have size 1
      val firstFile = partitionFiles.head

      val content = FileUtils.readLines(firstFile, StandardCharset.UTF_8).asScala.toSet

      // TODO (next PRs): aggregate by clientId
      content shouldBe Set(
        "client1,4",
        "client2,2"
      )
    }
  }

}
