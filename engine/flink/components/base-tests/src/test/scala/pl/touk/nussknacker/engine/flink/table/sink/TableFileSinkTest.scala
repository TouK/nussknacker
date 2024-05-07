package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
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
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.CollectionConverters._
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class TableFileSinkTest extends AnyFunSuite with FlinkSpec with Matchers with PatientScalaFutures {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private lazy val outputDirectory1 = Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-1")
  private lazy val outputDirectory2 = Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-2")
  private lazy val inputDirectory =
    new File("engine/flink/components/base-tests/src/test/resources/tables/primitives").toPath.toAbsolutePath

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
      |CREATE TABLE output1 WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$outputDirectory1',
      |      'format' = 'json'
      |) LIKE input;
      |
      |CREATE TABLE output2 WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$outputDirectory2',
      |      'format' = 'csv'
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

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(outputDirectory1.toFile)
    FileUtils.deleteQuietly(outputDirectory2.toFile)
    super.afterAll()
  }

  test("should do file-to-file ping-pong for all primitive types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> "'input'")
      .emptySink("end", "table", "Table" -> "'output1'", "Value" -> "#input")

    val result = runner.runWithoutData(scenario)
    result.isValid shouldBe true

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(outputDirectory1)
    val inputFileContent = getLinesOfSingleFileInDirectoryEventually(inputDirectory)

    outputFileContent shouldBe inputFileContent
  }

  test("should do spel-to-file for all primitive types") {
    val primitiveTypesRecordCsvFirstLine =
      """str,true,123,123,123,123,123.0,123.0,1,2020-12-31,10:15:00,"2020-12-31 10:15:00","2020-12-31 10:15:00Z""""

    val primitiveTypesExpression = Expression.spel("""
        |{
        |  boolean: true,
        |  string: "str",
        |  tinyInt: 123.byteValue,
        |  smallInt: 123.shortValue,
        |  int: 123,
        |  bigint: 123.longValue,
        |  decimal: T(java.math.BigDecimal).ONE,
        |  float: 123.floatValue,
        |  double: 123.doubleValue,
        |  date: T(java.time.LocalDate).parse("2020-12-31"),
        |  time: T(java.time.LocalTime).parse("10:15"),
        |  timestamp: T(java.time.LocalDateTime).parse("2020-12-31T10:15"),
        |  timestampLtz: T(java.time.Instant).parse("2020-12-31T10:15:00Z")
        |}
        |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .emptySink("end", "table", "Table" -> "'output2'", "Value" -> primitiveTypesExpression)

    val result = runner.runWithData(
      scenario = scenario,
      data = List("ignored"),
      boundedness = Boundedness.BOUNDED,
      flinkExecutionMode = Some(RuntimeExecutionMode.BATCH)
    )
    result.isValid shouldBe true

    val outputFileContentLines = getLinesOfSingleFileInDirectoryEventually(outputDirectory2)
    outputFileContentLines contains primitiveTypesRecordCsvFirstLine
  }

  private def getLinesOfSingleFileInDirectoryEventually(directory: Path) = {
    val outputFile = eventually {
      val files = Files.newDirectoryStream(directory).asScala.filterNot(Files.isHidden)
      files should have size 1
      files.head
    }
    Files.lines(outputFile, StandardCharsets.UTF_8).iterator().asScala.toList
  }

}
