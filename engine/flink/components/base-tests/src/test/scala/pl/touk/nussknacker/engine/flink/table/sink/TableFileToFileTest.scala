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

  private lazy val outputDirectory = Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}")
  private lazy val inputDirectory =
    new File("engine/flink/components/base-tests/src/test/resources/all-types/input").toPath.toAbsolutePath

  private lazy val tablesDefinition =
    s"""
      |CREATE TABLE input (
      |    `string`              STRING,
      |    `boolean`             BOOLEAN,
      |    `tinyInt`             TINYINT,
      |    `smallInt`            SMALLINT,
      |    `int`                 INT,
      |    `bigint`              BIGINT,
      |    `float`               FLOAT,
      |    `double`              DOUBLE,
      |    `decimal`             DECIMAL,
      |    `date`                DATE,
      |    `time`                TIME,
      |    `timestamp`           TIMESTAMP,
      |    `timestampLtz`        TIMESTAMP_LTZ
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$inputDirectory',
      |      'format' = 'json'
      |);
      |
      |CREATE TABLE output WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$outputDirectory',
      |      'format' = 'json'
      |) LIKE input;
      |
      |""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempFile = File.createTempFile("tables-definition", ".sql")
    tempFile.deleteOnExit()
    FileUtils.writeStringToFile(tempFile, tablesDefinition, StandardCharsets.UTF_8)
    tempFile.toPath
  }

  private lazy val tableComponentsConfig: Config = ConfigFactory.parseString(s"""
       |{
       |  tableDefinitionFilePath: $sqlTablesDefinitionFilePath
       |  enableFlinkBatchExecutionMode: true
       |}
       |""".stripMargin)

  private lazy val tableComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentsConfig,
    ProcessObjectDependencies.withConfig(tableComponentsConfig)
  )

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(outputDirectory.toFile)
    super.afterAll()
  }

  test("should do file-to-file ping-pong for all primitive types") {
    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .withExtraComponents(tableComponents)
      .build()

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> "'input'")
      .emptySink("end", "table", "Table" -> "'output'", "Value" -> "#input")

    val result = runner.runWithData(
      scenario = scenario,
      data = List.empty,
      boundedness = Boundedness.BOUNDED,
      flinkExecutionMode = Some(RuntimeExecutionMode.BATCH)
    )

    result.isValid shouldBe true
    val outputFile = eventually {
      val files = Option(outputDirectory.toFile.listFiles(!_.isHidden)).toList.flatten
      files should have size 1
      files
    }
    val outputFileContent = FileUtils.readFileToString(outputFile.head, StandardCharsets.UTF_8)
    val inputFileContent  = FileUtils.readFileToString(inputDirectory.toFile.listFiles().head, StandardCharsets.UTF_8)

    outputFileContent shouldBe inputFileContent
  }

}
