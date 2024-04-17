package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.PatientScalaFutures
import org.apache.commons.io.FileUtils
import scala.jdk.CollectionConverters._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TableFileToFileTest extends AnyFunSuite with FlinkSpec with Matchers with Inside with PatientScalaFutures {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-transactions_summary-")
  private lazy val inputFilePath =
    new File("engine/flink/components/base-tests/src/test/resources/transactions").toPath.toAbsolutePath

  private lazy val tablesDefinition =
    s"""
      |CREATE TABLE transactions
      |(
      |    client_id STRING,
      |    amount    INT
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$inputFilePath',
      |      'format' = 'csv'
      |);
      |CREATE TABLE transactions_summary
      |(
      |    client_id STRING,
      |    amount    INT
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$outputDirectory',
      |      'format' = 'csv'
      |);
      |
      |""".stripMargin

  private lazy val kafkaTableConfig =
    s"""
       |{
       |  tableDefinitionFilePath: $sqlTablesDefinitionFilePath
       |  enableFlinkBatchExecutionMode: true
       |}
       |""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempFile = File.createTempFile("tables-definition", ".sql")
    tempFile.deleteOnExit()
    FileUtils.writeStringToFile(tempFile, tablesDefinition, StandardCharsets.UTF_8)
    tempFile.toPath
  }

  private lazy val tableKafkaComponentsConfig: Config = ConfigFactory.parseString(kafkaTableConfig)

  private lazy val additionalComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableKafkaComponentsConfig,
    ProcessObjectDependencies.withConfig(tableKafkaComponentsConfig)
  )

  test("file to file") {
    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .withExtraComponents(additionalComponents)
      .build()

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> "'transactions'")
      .emptySink("end", "table", "Table" -> "'transactions_summary'", "Value" -> "#input")

    val result = runner.runWithData(
      scenario,
      List.empty,
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    result.isValid shouldBe true
    val transactionsSummaryFiles = eventually {
      val files = Option(outputDirectory.toFile.listFiles(!_.isHidden)).toList.flatten
      files should have size 1
      files
    }
    val transactionsSummaryContentLines =
      FileUtils.readLines(transactionsSummaryFiles.head, StandardCharsets.UTF_8).asScala.toSet
    transactionsSummaryContentLines shouldBe Set(
      "client1,1",
      "client2,2",
      "client1,3"
    )
  }

}
