package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import org.apache.commons.io.FileUtils
import org.apache.flink.api.connector.source.Boundedness
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

  private val basicPingPongInputTableName    = "basic-ping-pong-input"
  private val basicPingPongOutputTableName   = "basic-ping-pong-output"
  private val basicExpressionOutputTableName = "basic-expression-output"

  private val advancedPingPongInputTableName    = "advanced-ping-pong-input"
  private val advancedPingPongOutputTableName   = "advanced-ping-pong-output"
  private val advancedExpressionOutputTableName = "advanced-expression-output"

  private val datetimePingPongInputTableName    = "datetime-ping-pong-input"
  private val datetimePingPongOutputTableName   = "datetime-ping-pong-output"
  private val datetimeExpressionOutputTableName = "datetime-expression-output"

  private val virtualColumnInputTableName  = "virtual-column-input"
  private val virtualColumnOutputTableName = "virtual-column-output"

  private val oneColumnOutputTableName = "one-column-output"
  private val genericsOutputTableName  = "generics-output"

  private lazy val basicPingPongInputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$basicPingPongInputTableName")
  private lazy val basicPingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$basicPingPongOutputTableName")
  private lazy val basicExpressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$basicExpressionOutputTableName")

  private lazy val advancedPingPongInputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedPingPongInputTableName")
  private lazy val advancedPingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedPingPongOutputTableName")
  private lazy val advancedExpressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$advancedExpressionOutputTableName")

  private lazy val datetimePingPongInputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$datetimePingPongInputTableName")
  private lazy val datetimePingPongOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$datetimePingPongOutputTableName")
  private lazy val datetimeExpressionOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$datetimeExpressionOutputTableName")

  private lazy val virtualColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$virtualColumnOutputTableName")

  private lazy val oneColumnOutputDirectory =
    Files.createTempDirectory(s"nusssknacker-${getClass.getSimpleName}-$oneColumnOutputTableName")

  private lazy val tablesDefinition =
    s"""
      |CREATE TABLE `$basicPingPongInputTableName` (
      |    `string`              STRING,
      |    `boolean`             BOOLEAN,
      |    `tinyInt`             TINYINT,
      |    `smallInt`            SMALLINT,
      |    `int`                 INT,
      |    `bigint`              BIGINT,
      |    `float`               FLOAT,
      |    `double`              DOUBLE,
      |    `decimal`             DECIMAL
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$basicPingPongInputDirectory',
      |      'format' = 'json'
      |);
      |
      |CREATE TABLE `$basicPingPongOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$basicPingPongOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$basicPingPongInputTableName`;
      |
      |CREATE TABLE `$basicExpressionOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$basicExpressionOutputDirectory',
      |      'format' = 'csv'
      |) LIKE `$basicPingPongInputTableName`;
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
      |
      |CREATE TABLE `$datetimePingPongInputTableName` (
      |      `date` DATE,
      |      `time` TIME,
      |      `timestamp` TIMESTAMP,
      |      `timestamp_ltz` TIMESTAMP_LTZ
      |) WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$datetimePingPongInputDirectory',
      |      'format' = 'json'
      |);
      |
      |CREATE TABLE `$datetimePingPongOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$datetimePingPongOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$datetimePingPongInputTableName`;
      |
      |CREATE TABLE `$datetimeExpressionOutputTableName` WITH (
      |      'connector' = 'filesystem',
      |      'path' = 'file:///$datetimeExpressionOutputDirectory',
      |      'format' = 'json'
      |) LIKE `$datetimePingPongInputTableName`;
      |
      |CREATE DATABASE testdb;
      |
      |CREATE TABLE testdb.tablewithqualifiedname (
      |      `quantity` INT
      |) WITH (
      |    'connector' = 'datagen',
      |    'number-of-rows' = '1'
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
    .withExtraComponents(tableComponents)
    .build()

  override protected def afterAll(): Unit = {
    FileUtils.deleteQuietly(basicPingPongInputDirectory.toFile)
    FileUtils.deleteQuietly(basicPingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(basicExpressionOutputDirectory.toFile)
    FileUtils.deleteQuietly(advancedPingPongInputDirectory.toFile)
    FileUtils.deleteQuietly(advancedPingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(advancedExpressionOutputDirectory.toFile)
    FileUtils.deleteQuietly(datetimePingPongInputDirectory.toFile)
    FileUtils.deleteQuietly(datetimePingPongOutputDirectory.toFile)
    FileUtils.deleteQuietly(datetimeExpressionOutputDirectory.toFile)
    FileUtils.deleteQuietly(oneColumnOutputDirectory.toFile)
    FileUtils.deleteQuietly(virtualColumnOutputDirectory.toFile)
    super.afterAll()
  }

  test("should do file-to-file ping-pong for all basic types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "table",
        "Table" -> s"'`default_catalog`.`default_database`.`$basicPingPongInputTableName`'".spel
      )
      .buildVariable(
        "example-transformations",
        "out",
        "string"   -> "#input.string.length".spel,
        "boolean"  -> "#input.boolean.booleanValue".spel,
        "tinyInt"  -> "#input.tinyInt.intValue".spel,
        "smallInt" -> "#input.smallInt.intValue".spel,
        "int"      -> "#input.int.intValue".spel,
        "bigint"   -> "#input.bigint.intValue".spel,
        "float"    -> "#input.float.intValue".spel,
        "double"   -> "#input.double.intValue".spel,
        "decimal"  -> "#input.decimal.longValueExact".spel
      )
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$basicPingPongOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val inputContent = Json
      .fromFields(
        List(
          "string" -> Json.fromString(
            "bd23cc017c0927ae2a7769177eb0cf2ae6aa2d01013e142170f61c5659abd37665bb44dc47c525750673a80a6d48fcd9e665"
          ),
          "boolean"  -> Json.fromBoolean(true),
          "tinyInt"  -> Json.fromInt(-1),
          "smallInt" -> Json.fromInt(-11796),
          "int"      -> Json.fromInt(1781149910),
          "bigint"   -> Json.fromLong(6740817575276386000L),
          "float"    -> Json.fromDoubleOrNull(2.0957512e+38),
          "double"   -> Json.fromDoubleOrNull(6.366705250892882e+307),
          "decimal"  -> Json.fromLong(2312106163L),
        )
      )
      .noSpaces
    Files.writeString(basicPingPongInputDirectory.resolve("file.json"), inputContent, StandardCharsets.UTF_8)

    val result = runner.runWithoutData(scenario)
    result.validValue.errors shouldBe empty

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(basicPingPongOutputDirectory).loneElement

    outputFileContent shouldBe inputContent
  }

  test("should be able to access virtual columns in input table") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "table",
        "Table" -> s"'`default_catalog`.`default_database`.`$virtualColumnInputTableName`'".spel
      )
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$virtualColumnOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val result = runner.runWithoutData(scenario)
    result.validValue.errors shouldBe empty

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(virtualColumnOutputDirectory)

    val quantityStr :: priceStr :: costStr :: Nil = outputFileContent.loneElement.split(",").toList
    val expectedCost                              = quantityStr.toInt * priceStr.toDouble
    costStr.toDouble shouldEqual expectedCost
  }

  test("should do spel-to-file for all basic types") {
    val basicTypesRecordCsvFirstLine =
      "str," +
        "true," +
        "123," +
        "123," +
        "123," +
        "123," +
        "123.12," +
        "123.12," +
        "1"

    val basicTypesExpression = Expression.spel(s"""
        |{
        |  boolean: ${spelBoolean.expression},
        |  string: ${spelStr.expression},
        |  tinyInt: ${spelByte.expression},
        |  smallInt: ${spelShort.expression},
        |  int: ${spelInt.expression},
        |  bigint: ${spelLong.expression},
        |  decimal: ${spelBigDecimal.expression},
        |  float:  ${spelFloat.expression},
        |  double: ${spelDouble.expression}
        |}
        |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$basicExpressionOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> basicTypesExpression
      )

    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result shouldBe Symbol("valid")

    getLinesOfSingleFileInDirectoryEventually(
      basicExpressionOutputDirectory
    ).loneElement shouldBe basicTypesRecordCsvFirstLine
  }

  test("should skip redundant fields") {
    val valueExpression = Expression.spel(s"""
         |{
         |  two: 234,
         |  one: 123
         |}
         |""".stripMargin)

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$oneColumnOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty

    getLinesOfSingleFileInDirectoryEventually(oneColumnOutputDirectory).loneElement shouldBe "123"
  }

  test("should do file-to-file ping-pong for advanced types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "table",
        "Table" -> s"'`default_catalog`.`default_database`.`$advancedPingPongInputTableName`'".spel
      )
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
        "Table"      -> s"'`default_catalog`.`default_database`.`$advancedPingPongOutputTableName`'".spel,
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
    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty

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
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$advancedExpressionOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty

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

  test("should do file-to-file ping-pong for datetime types") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "start",
        "table",
        "Table" -> s"'`default_catalog`.`default_database`.`$datetimePingPongInputTableName`'".spel
      )
      .buildVariable(
        "example-transformations",
        "out",
        "date"          -> "#input.date.atStartOfDay".spel,
        "time"          -> "#input.time.atDate('2024-01-01')".spel,
        "timestamp"     -> "#input.timestamp.toLocalDate".spel,
        "timestamp_ltz" -> "#input.timestamp_ltz.toEpochMilli".spel
      )
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$datetimePingPongOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    val inputContent = Json
      .fromFields(
        List(
          "date"          -> Json.fromString("2024-01-01"),
          "time"          -> Json.fromString("12:01:02.000000003"),
          "timestamp"     -> Json.fromString("2024-01-01 12:01:02.000000003"),
          "timestamp_ltz" -> Json.fromString("2024-01-01 12:01:02.000000003Z"),
        )
      )
      .noSpaces
    val expectedContent = Json
      .fromFields(
        List(
          "date" -> Json.fromString("2024-01-01"),
          // CREATE TABLE statement doesn't take into consideration fractional seconds precision - it is always TIME(0)
          "time"          -> Json.fromString("12:01:02"),
          "timestamp"     -> Json.fromString("2024-01-01 12:01:02.000000003"),
          "timestamp_ltz" -> Json.fromString("2024-01-01 12:01:02.000000003Z"),
        )
      )
      .noSpaces

    Files.writeString(datetimePingPongInputDirectory.resolve("file.json"), inputContent, StandardCharsets.UTF_8)
    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(datetimePingPongOutputDirectory)

    outputFileContent.loneElement shouldBe expectedContent
  }

  test("should be possible to provide values for datetime types by spel expressions") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"         -> s"'`default_catalog`.`default_database`.`$datetimeExpressionOutputTableName`'".spel,
        "Raw editor"    -> "false".spel,
        "date"          -> "T(java.time.LocalDate).parse('2024-01-01')".spel,
        "time"          -> "T(java.time.LocalTime).parse('12:01:02.000000003')".spel,
        "timestamp"     -> "T(java.time.LocalDateTime).parse('2024-01-01T12:01:02.000000003')".spel,
        "timestamp_ltz" -> "T(java.time.Instant).parse('2024-01-01T12:01:02.000000003Z')".spel,
      )

    val expectedContent = Json
      .fromFields(
        List(
          "date" -> Json.fromString("2024-01-01"),
          // CREATE TABLE statement doesn't take into consideration fractional seconds precision - it is always TIME(0)
          "time"          -> Json.fromString("12:01:02"),
          "timestamp"     -> Json.fromString("2024-01-01 12:01:02.000000003"),
          "timestamp_ltz" -> Json.fromString("2024-01-01 12:01:02.000000003Z"),
        )
      )
      .noSpaces

    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.errors shouldBe empty

    val outputFileContent = getLinesOfSingleFileInDirectoryEventually(datetimeExpressionOutputDirectory)

    outputFileContent.loneElement shouldBe expectedContent
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
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$genericsOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)

    val expectedMessage =
      """Provided value does not match scenario output - errors:
        |Incorrect type: actual: 'Record{arrayOfInts: List[Double]({1.23, 2.34}), map: Record{foo: Double(1.23)}}' expected: 'Record{arrayOfInts: List[Integer], map: Map[String,Integer]}'.""".stripMargin
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
      .source("source", TestScenarioRunner.testDataSource)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'`default_catalog`.`default_database`.`$oneColumnOutputTableName`'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> valueExpression
      )

    val result  = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
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
