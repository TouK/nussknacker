package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import org.apache.commons.io.FileUtils
import org.apache.flink.table.api.DataTypes
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.TestTableComponents._
import pl.touk.nussknacker.engine.flink.table.utils.NotConvertibleResultOfAlignmentException
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.testmode.TestProcess.ExceptionResult
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

class TableFileSinkTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with PatientScalaFutures
    with LoneElement
    with ValidatedValuesDetailedMessage {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val pingPongInputTableName         = "ping-pong-input"
  private val advancedPingPongInputTableName = "advanced-ping-pong-input"
  private val virtualColumnInputTableName    = "virtual-column-input"

  private val pingPongOutputTableName           = "ping-pong-output"
  private val rowFieldAccessOutputTableName     = "row-field-access-output"
  private val expressionOutputTableName         = "expression-output"
  private val oneColumnOutputTableName          = "one-column-output"
  private val virtualColumnOutputTableName      = "virtual-column-output"
  private val genericsOutputTableName           = "generics-output"
  private val advancedPingPongOutputTableName   = "advanced-ping-pong-output"
  private val advancedExpressionOutputTableName = "advanced-expression-output"

  private lazy val pingPongInputDirectory =
    new File("engine/flink/components/base-tests/src/test/resources/tables/primitives").toPath.toAbsolutePath
  private lazy val advancedPingPongInputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedPingPongInputTableName")

  private lazy val pingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$pingPongOutputTableName")
  private lazy val rowFieldAccessOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$rowFieldAccessOutputTableName")
  private lazy val expressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$expressionOutputTableName")
  private lazy val oneColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$oneColumnOutputTableName")
  private lazy val virtualColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$virtualColumnOutputTableName")
  private lazy val advancedPingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedPingPongOutputTableName")
  private lazy val advancedExpressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedExpressionOutputTableName")

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
      |      `one` INT
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$oneColumnOutputDirectory',
      |      'format' = 'csv'
      |);
      |
      |CREATE TABLE `$virtualColumnInputTableName` (
      |      `quantity` INT,
      |      `price` DOUBLE,
      |      `cost` AS quantity * price
      |) WITH (
      |    'connector' = 'datagen',
      |    'number-of-rows' = '1'
      |);
      |
      |CREATE TABLE `$virtualColumnOutputTableName` (
      |      `quantity` INT,
      |      `price` DOUBLE,
      |      `cost` DOUBLE
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$virtualColumnOutputDirectory',
      |      'format' = 'csv'
      |);
      |
      |CREATE TABLE `$genericsOutputTableName` (
      |      `arrayOfInts` ARRAY<INT>,
      |      `map` MAP<STRING, INT>
      |) WITH (
      |      'connector' = 'blackhole'
      |);
      |
      |CREATE TABLE `$advancedPingPongInputTableName` (
      |      `nullable` INT,
      |      `arrayOfInts` ARRAY<INT>,
      |      `arrayOfStrings` ARRAY<STRING>,
      |      `arrayOfRows`ARRAY<ROW<foo INT, bar STRING>>,
      |      `decimal` DECIMAL(15,2),
      |      `map` MAP<STRING, INT>,
      |      `multiset` MULTISET<STRING>
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$advancedPingPongInputDirectory',
      |      'format' = 'json'
      |);
      |
      |CREATE TABLE `$advancedPingPongOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$advancedPingPongOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$advancedPingPongInputTableName`;
      |
      |CREATE TABLE `$advancedExpressionOutputTableName` (
      |      `nullable` INT,
      |      `missing` INT,
      |      `arrayOfInts` ARRAY<INT>,
      |      `arrayOfStrings` ARRAY<STRING>,
      |      `arrayOfRows` ARRAY<ROW<foo INT, bar STRING>>,
      |      `decimal` DECIMAL(15,2),
      |      `decimalFromInt` DECIMAL(15,2),
      |      `intFromDecimal` INT,
      |      `intFromUnknown` INT,
      |      `map` MAP<STRING, INT>,
      |      `multiset` MULTISET<STRING>
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$advancedExpressionOutputDirectory',
      |      'format' = 'json'
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
       |}
       |""".stripMargin)

  private lazy val tableComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentsConfig,
    ProcessObjectDependencies.withConfig(tableComponentsConfig)
  )

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(singleRecordBatchTable :: tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(pingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(expressionOutputDirectory.toFile)
    FileUtils.deleteQuietly(oneColumnOutputDirectory.toFile)
    FileUtils.deleteQuietly(virtualColumnOutputDirectory.toFile)
    FileUtils.deleteQuietly(advancedPingPongInputDirectory.toFile)
    FileUtils.deleteQuietly(advancedPingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(advancedExpressionOutputDirectory.toFile)
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

  test("should be able to access virtual columns in input table") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$virtualColumnInputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$virtualColumnOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(virtualColumnOutputDirectory)

    val quantityStr :: priceStr :: costStr :: Nil = outputFileContent.loneElement.split(",").toList
    val expectedCost                              = quantityStr.toInt * priceStr.toDouble
    costStr.toDouble shouldEqual expectedCost
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
         |  two: $spelInt,
         |  one: $spelInt
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
    result shouldBe Symbol("valid")

    getLinesOfSingleFileInDirectoryEventually(oneColumnOutputDirectory).loneElement shouldBe "123"
  }

  test("should do file-to-file ping-pong for advanced types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$advancedPingPongInputTableName'".spel)
      .buildVariable(
        "example-transformations",
        "out",
        "arrayOfInts"    -> "#input.arrayOfInts.![#this + 1]".spel,
        "arrayOfStrings" -> "#input.arrayOfStrings.![#this.length]".spel,
        "arrayOfRows"    -> "#input.arrayOfRows.![#this.foo + 1][0]".spel,
        "decimal"        -> "#input.decimal.precision".spel
      )
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$advancedPingPongOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val nestedRecord = Json.fromFields(
      List(
        "bar"       -> Json.fromString("ala"),
        "foo"       -> Json.fromInt(123),
        "redundant" -> Json.fromString("redundantField"),
      )
    )
    val inputContent = Json
      .fromFields(
        List(
          "nullable"       -> Json.Null,
          "arrayOfInts"    -> Json.fromValues(List(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))),
          "arrayOfStrings" -> Json.fromValues(List(Json.fromString("foo"), Json.fromString("bar"))),
          "arrayOfRows"    -> Json.fromValues(List(nestedRecord)),
          "decimal"        -> Json.fromDoubleOrNull(1.234),
          "map"            -> Json.fromFields(List("foo" -> Json.fromDoubleOrNull(1.23))),
          "multiset"       -> Json.fromFields(List("foo" -> Json.fromInt(1), "bar" -> Json.fromInt(2))),
          "redundant"      -> Json.fromString("redundantField"),
        )
      )
      .noSpaces
    val expectedNestedRecord = Json.fromFields(
      List(
        "foo" -> Json.fromInt(123),
        "bar" -> Json.fromString("ala")
      )
    )
    val expectedContent = Json
      .fromFields(
        List(
          "nullable"       -> Json.Null,
          "arrayOfInts"    -> Json.fromValues(List(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))),
          "arrayOfStrings" -> Json.fromValues(List(Json.fromString("foo"), Json.fromString("bar"))),
          "arrayOfRows"    -> Json.fromValues(List(expectedNestedRecord)),
          "decimal"        -> Json.fromDoubleOrNull(1.23),
          "map"            -> Json.fromFields(List("foo" -> Json.fromInt(1))),
          "multiset"       -> Json.fromFields(List("bar" -> Json.fromInt(2), "foo" -> Json.fromInt(1)))
        )
      )
      .noSpaces

    Files.writeString(advancedPingPongInputDirectory.resolve("file.json"), inputContent, StandardCharsets.UTF_8)
    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(advancedPingPongOutputDirectory)

    outputFileContent.loneElement shouldBe expectedContent
  }

  test("should align types in advanced types used in expressions") {
    val valueExpression = Expression.spel(s"""
                                             |{
                                             |  nullable: null,
                                             |  arrayOfInts: {1, 2, 3},
                                             |  arrayOfStrings: {"foo", "bar"},
                                             |  arrayOfRows: {{bar: "ala", foo: 123, redundant: "redundantField"}},
                                             |  decimal: 1.234,
                                             |  decimalFromInt: 123,
                                             |  intFromDecimal: T(java.math.BigDecimal).ONE,
                                             |  intFromUnknown: {1, "ala"}[0],
                                             |  map: {foo: T(java.math.BigDecimal).ONE},
                                             |  multiset: {foo: T(java.math.BigDecimal).ONE},
                                             |  redundant: "redundantField"
                                             |}
                                             |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$advancedExpressionOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithoutData(
      scenario = scenario
    )
    result shouldBe Symbol("valid")

    val expectedNestedRecord = Json.fromFields(
      List(
        "foo" -> Json.fromInt(123),
        "bar" -> Json.fromString("ala")
      )
    )
    val expectedContent = Json
      .fromFields(
        List(
          "nullable"       -> Json.Null,
          "missing"        -> Json.Null,
          "arrayOfInts"    -> Json.fromValues(List(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))),
          "arrayOfStrings" -> Json.fromValues(List(Json.fromString("foo"), Json.fromString("bar"))),
          "arrayOfRows"    -> Json.fromValues(List(expectedNestedRecord)),
          "decimal"        -> Json.fromDoubleOrNull(1.23),
          "decimalFromInt" -> Json.fromInt(123),
          "intFromDecimal" -> Json.fromInt(1),
          "intFromUnknown" -> Json.fromInt(1),
          "map"            -> Json.fromFields(List("foo" -> Json.fromInt(1))),
          "multiset"       -> Json.fromFields(List("foo" -> Json.fromInt(1))),
        )
      )
      .noSpaces
    getLinesOfSingleFileInDirectoryEventually(
      advancedExpressionOutputDirectory
    ).loneElement shouldBe expectedContent
  }

  test("should not allow to pass floating point types into decimal types in generics") {
    val valueExpression = Expression.spel(s"""
                                             |{
                                             |  arrayOfInts: {1.23, 2.34},
                                             |  map: {foo: 1.23}
                                             |}
                                             |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$genericsOutputTableName'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithoutData(
      scenario = scenario
    )

    val expectedMessage =
      """Provided value does not match scenario output - errors:
        |Incorrect type: actual: 'Record{arrayOfInts: List[Double]({1.23, 2.34}), map: Record{foo: Double(1.23)}}' expected: 'Record{arrayOfInts: Array[Integer], map: Map[String,Integer]}'.""".stripMargin
    result.invalidValue.toList should matchPattern {
      case CustomNodeError(
            "end",
            `expectedMessage`,
            Some(ParameterName("Value"))
          ) :: Nil =>
    }
  }

  test("should handle errors during alignment") {
    val valueExpression = Expression.spel(s"""{
                                             |  one: {1, "ala"}[1]
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
    val intType = DataTypes.INT().getLogicalType
    result.validValue.errors should matchPattern {
      case ExceptionResult(_, Some("end"), NotConvertibleResultOfAlignmentException("ala", "ala", `intType`)) :: Nil =>
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
