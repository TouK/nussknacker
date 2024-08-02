package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.TestTableComponents._
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.PatientScalaFutures

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class TableFileSinkTest extends AnyFunSuite with FlinkSpec with Matchers with PatientScalaFutures with LoneElement {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val pingPongInputTableName        = "ping-pong-input"
  private val pingPongOutputTableName       = "ping-pong-output"
  private val rowFieldAccessOutputTableName = "row-field-access-output"
  private val expressionOutputTableName     = "expression-output"
  private val oneColumnOutputTableName      = "one-column-output"

  private lazy val pingPongInputDirectory =
    new File("engine/flink/components/base-tests/src/test/resources/tables/primitives").toPath.toAbsolutePath
  private lazy val pingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$pingPongOutputTableName")
  private lazy val rowFieldAccessOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$pingPongOutputTableName")
  private lazy val expressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$expressionOutputTableName")
  private lazy val oneColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$oneColumnOutputTableName")

  private lazy val tablesDefinition =
    s"""
      |CREATE TABLE `$pingPongInputTableName` (
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
      |      'path' = 'file:///$pingPongInputDirectory',
      |      'format' = 'json'
      |);
      |
      |CREATE TABLE `$pingPongOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$pingPongOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$pingPongInputTableName`;
      |
      |CREATE TABLE `$rowFieldAccessOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$rowFieldAccessOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$pingPongInputTableName`;
      |
      |CREATE TABLE `$expressionOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$expressionOutputDirectory',
      |      'format' = 'csv'
      |) LIKE `$pingPongInputTableName`;
      |
      |CREATE TABLE `$oneColumnOutputTableName` (
      |    `one`                 STRING
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$oneColumnOutputDirectory',
      |      'format' = 'csv'
      |);
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
    .withExtraComponents(singleRecordBatchTable :: tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(pingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(expressionOutputDirectory.toFile)
    FileUtils.deleteQuietly(oneColumnOutputDirectory.toFile)
    super.afterAll()
  }

  test("should do file-to-file ping-pong for all primitive types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$pingPongInputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$pingPongOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(pingPongOutputDirectory)
    val inputFileContent  = getLinesOfSingleFileInDirectoryEventually(pingPongInputDirectory)

    outputFileContent shouldBe inputFileContent
  }

  test("should allow to access fields of Row produced by source") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$pingPongInputTableName'".spel)
      .buildSimpleVariable("variable", "someVar", "#input.string.length".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$rowFieldAccessOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(rowFieldAccessOutputDirectory)
    val inputFileContent  = getLinesOfSingleFileInDirectoryEventually(pingPongInputDirectory)

    outputFileContent shouldBe inputFileContent
  }

  test("should do spel-to-file for all primitive types") {
    val primitiveTypesRecordCsvFirstLine =
      "str," +
        "true," +
        "123," +
        "123," +
        "123," +
        "123," +
        "123.12," +
        "123.12," +
        "1," +
        "2020-12-31,10:15:00," +
        "\"2020-12-31 10:15:00\"," +
        "\"2020-12-31 10:15:00Z\""

    val primitiveTypesExpression = Expression.spel(s"""
        |{
        |  boolean: $spelBoolean,
        |  string: $spelStr,
        |  tinyInt: $spelByte,
        |  smallInt: $spelShort,
        |  int: $spelInt,
        |  bigint: $spelLong,
        |  decimal: $spelBigDecimal,
        |  float:  $spelFloat,
        |  double: $spelDouble,
        |  date: $spelLocalDate,
        |  time: $spelLocalTime,
        |  timestamp: $spelLocalDateTime,
        |  timestampLtz: $spelInstant
        |}
        |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$expressionOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> primitiveTypesExpression
      )

    val result = runner.runWithoutData(
      scenario = scenario
    )
    result shouldBe Symbol("valid")

    getLinesOfSingleFileInDirectoryEventually(
      expressionOutputDirectory
    ).loneElement shouldBe primitiveTypesRecordCsvFirstLine
  }

  test("should skip redundant fields") {
    val valueExpression = Expression.spel(s"""
         |{
         |  two: $spelStr,
         |  one: $spelStr
         |}
         |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$oneColumnOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithoutData(
      scenario = scenario
    )
    result.isValid shouldBe true

    getLinesOfSingleFileInDirectoryEventually(oneColumnOutputDirectory).loneElement shouldBe "str"
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
