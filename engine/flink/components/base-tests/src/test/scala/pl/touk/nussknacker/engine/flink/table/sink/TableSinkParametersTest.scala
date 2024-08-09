package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  ExpressionParserCompilationError
}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.jdk.CollectionConverters._
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class TableSinkParametersTest extends AnyFunSuite with FlinkSpec with Matchers with PatientScalaFutures {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val inputTableName                 = "input"
  private val outputTableName                = "output"
  private val virtualColumnOutputTableName   = "virtual-column-output"
  private val outputTableNameWithInvalidCols = "output_invalid_column_names"

  private lazy val outputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$outputTableName")
  private lazy val virtualColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$virtualColumnOutputTableName")

  private lazy val tablesDefinition =
    s"""CREATE TABLE $inputTableName (
       |    client_id STRING,
       |    amount  DECIMAL(15,2)
       |) WITH (
       |    'connector' = 'datagen',
       |    'number-of-rows' = '1'
       |);
       |
       |CREATE TABLE $outputTableNameWithInvalidCols
       |(
       |    `Raw editor`  STRING,
       |    `Table`       STRING
       |) WITH (
       |    'connector' = 'blackhole'
       |);
       |
       |CREATE TABLE $outputTableName (
       |    client_id STRING,
       |    amount  DECIMAL(15,2)
       |)  WITH (
       |      'connector' = 'filesystem',
       |      'path' = 'file:///$outputDirectory',
       |      'format' = 'csv'
       |);
       |
       |CREATE TABLE `$virtualColumnOutputTableName` (
       |      `quantity` INT,
       |      `price` DOUBLE,
       |      `cost` AS quantity * price
       |) WITH (
       |      'connector' = 'filesystem',
       |      'path' = 'file:///$virtualColumnOutputDirectory',
       |      'format' = 'csv'
       |);
       |""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempFile = File.createTempFile("tables-definition", ".sql")
    tempFile.deleteOnExit()
    FileUtils.writeStringToFile(tempFile, tablesDefinition, StandardCharsets.UTF_8)
    tempFile.toPath
  }

  private lazy val tableComponentsConfig: Config = ConfigFactory.parseString(
    s"""
       |{
       |  tableDefinitionFilePath: $sqlTablesDefinitionFilePath
       |}
       |""".stripMargin
  )

  private lazy val tableComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentsConfig,
    ProcessObjectDependencies.withConfig(tableComponentsConfig)
  )

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(outputDirectory.toFile)
    FileUtils.deleteQuietly(virtualColumnOutputDirectory.toFile)
    super.afterAll()
  }

  test("should take parameters per column in non-raw mode") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableName'".spel,
        "Raw editor" -> "false".spel,
        "client_id"  -> "''".spel,
        "amount"     -> "T(java.math.BigDecimal).ONE".spel,
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(outputDirectory)
    outputFileContent shouldEqual List(",1")
  }

  test("should skip virtual columns in listed parameters in non-raw mode") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$virtualColumnOutputTableName'".spel,
        "Raw editor" -> "false".spel,
        "quantity"   -> "2".spel,
        "price"      -> "1.5".spel,
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(virtualColumnOutputDirectory)
    outputFileContent shouldEqual List("2,1.5")
  }

  test("should return errors for type errors in non-raw mode value parameters") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableName'".spel,
        "Raw editor" -> "false".spel,
        "client_id"  -> "123.11".spel,
        "amount"     -> "''".spel,
      )

    val result = runner.runWithoutData(scenario)
    inside(result) {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Bad expression type, expected: String, found: Double(123.11)",
                "end",
                _,
                _,
                _
              ),
              ExpressionParserCompilationError(
                "Bad expression type, expected: BigDecimal, found: String()",
                "end",
                _,
                _,
                _
              ) :: Nil
            )
          ) =>
    }
  }

  test("should return errors for illegally named columns for non-raw mode") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableNameWithInvalidCols'".spel,
        "Raw editor" -> "false".spel
      )

    val result = runner.runWithoutData(scenario)
    inside(result) { case Invalid(NonEmptyList(CustomNodeError("end", _, _), CustomNodeError("end", _, _) :: Nil)) =>
    }
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
