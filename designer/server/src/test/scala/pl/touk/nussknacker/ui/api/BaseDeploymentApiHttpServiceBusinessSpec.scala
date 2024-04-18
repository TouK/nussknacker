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

import java.nio.file.Files

trait BaseDeploymentApiHttpServiceBusinessSpec extends WithFlinkContainersDeploymentManager {
  self: Suite with LazyLogging with Matchers with Eventually =>

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

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(outputDirectory.toFile) // it might not work because docker user can has other uid
    super.afterAll()
  }

  protected def outputTransactionSummaryContainsResult(): Unit = {
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

    eventually {
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

}
