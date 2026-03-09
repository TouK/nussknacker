package pl.touk.nussknacker.engine.flink.table.sql

import com.typesafe.config.ConfigFactory
import org.apache.flink.types.Row
import org.scalatest.{BeforeAndAfterAll, Inside, LoneElement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  ExpressionParserCompilationError
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.time._
import java.util.UUID

class FlinkSqlQueryComponentTest
    extends AnyFunSuite
    with Matchers
    with Inside
    with LoneElement
    with BeforeAndAfterAll
    with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] = new FlinkSqlOpsComponentProvider().components

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExecutionMode(ExecutionMode.Streaming)
    .withExtraComponents(additionalComponents)
    .build()

  test("should do select on non-record variable from record table") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> "SELECT r.input FROM record r".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithData(scenario, List(1, 2, 3))

    result.validValue.successes shouldBe List(
      Map("input" -> 1).toRow,
      Map("input" -> 2).toRow,
      Map("input" -> 3).toRow,
    )
  }

  test("should allow spel operations on sql output record") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> "SELECT input FROM record".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out.getField(0) + 1".spel)

    val result = runner.runWithData(scenario, List(1, 2, 3))

    result.validValue.successes shouldBe List(2, 3, 4)
  }

  test("should allow accessing fields on records") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> "SELECT input.keyA FROM record".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val inputType   = Typed.record(List("keyA" -> Typed[String]))
    val inputValues = List(Map("keyA" -> "valueA").asJava, Map("keyA" -> "valueB").asJava)

    val result = runner.runWithDataWithType[java.util.Map[String, String], Row](
      scenario,
      inputValues,
      inputType
    )

    result.validValue.successes shouldBe List(
      Map("keyA" -> "valueA").toRow,
      Map("keyA" -> "valueB").toRow,
    )
  }

  test("should clear context") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> "SELECT * FROM record".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#input".spel)

    val result = runner.runWithData(
      scenario,
      List(1)
    )

    result.invalidValue.toList.loneElement should matchPattern {
      case ExpressionParserCompilationError("Unresolved reference 'input'", _, _, _, _) =>
    }
  }

  test("should raise error when 'record_time' variable is defined in context") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .buildSimpleVariable("variable", "record_time", "'a'".spel)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> "SELECT * FROM record".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithData(scenario, List(1))

    result.invalidValue.toList.loneElement should matchPattern {
      case CustomNodeError(
            NodeId("flink-sql-query"),
            "Variable 'record_time' is reserved by Flink SQL component for event-time handling. Please rename or remove this variable",
            None
          ) =>
    }
  }

  test("should raise validation error for invalid full sql query") {
    val invalidSqlCases = Table(
      ("sql", "expectedMessagePart"),
      (
        "",
        "Query cannot be empty"
      ),
      (
        "SELECT notExistingColumn FROM record",
        "SQL validation failed. From line 1, column 8 to line 1, column 24: Column 'notExistingColumn' not found in any table"
      ),
      (
        "SHOW FUNCTIONS",
        "Unsupported SQL query! sqlQuery() only accepts a single SQL query of type SELECT, UNION, INTERSECT, EXCEPT, VALUES, and ORDER_BY."
      ),
      (
        "SELECT COUNT(*) FROM record",
        "doesn't support consuming update changes which is produced by node"
      ),
      (
        "SELECT ABS() FROM record",
        "Invalid number of arguments to function 'ABS'. Was expecting 1 arguments"
      ),
      (
        "SELECT NON_EXISTING_FUNCTION(input) FROM record",
        "SQL validation failed. From line 1, column 8 to line 1, column 35: No match found for function signature NON_EXISTING_FUNCTION(<NUMERIC>)"
      ),
      (
        "SELECT 'str' + 123",
        "SQL validation failed. From line 1, column 8 to line 1, column 18: Cannot apply '+' to arguments of type '<CHAR(3)> + <INTEGER>'. Supported form(s): '<NUMERIC> + <NUMERIC>"
      )
    )

    forEvery(invalidSqlCases) { (sql: String, expectedErrorMessage: String) =>
      val scenario = ScenarioBuilder
        .streaming("test")
        .source("source", TestScenarioRunner.testDataSource)
        .customNode(
          id = "flink-sql-query",
          outputVar = "out",
          customNodeRef = "flink-sql-query",
          "flinkSqlQuery" -> sql.spelTemplate
        )
        .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

      val result = runner.runWithData(scenario, List(1))

      result.invalidValue.toList.loneElement should matchPattern {
        case CustomNodeError(NodeId("flink-sql-query"), errorMessage, Some(ParameterName("flinkSqlQuery")))
            if errorMessage.contains(expectedErrorMessage) =>
      }
    }
  }

  test("should allow group by aggregation") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" ->
          """|SELECT
             |  input AS key,
             |  COUNT(*) AS `count`
             |FROM TABLE(
             |  TUMBLE(TABLE record, DESCRIPTOR(record_time), INTERVAL '1' DAY)
             |)
             |GROUP BY input, window_start, window_end""".stripMargin.spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithData[Int, Row](
      scenario,
      List(1, 2, 2, 3, 3, 3)
    )

    result.validValue.successes.toSet shouldBe Set(
      Map("key" -> 1, "count" -> 1L).toRow,
      Map("key" -> 2, "count" -> 2L).toRow,
      Map("key" -> 3, "count" -> 3L).toRow,
    )
  }

  test("should allow over aggregation") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" ->
          """|SELECT
             |  input.num AS num,
             |  SUM(input.num) OVER (
             |    ORDER BY record_time
             |  ) AS running_sum
             |FROM record""".stripMargin.spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val inputType = Typed.record(List("num" -> Typed[Long], "timestamp" -> Typed[Long]))
    val inputValues = List(
      Map("num" -> 1L, "timestamp" -> 1L).asJava,
      Map("num" -> 2L, "timestamp" -> 2L).asJava,
      Map("num" -> 3L, "timestamp" -> 3L).asJava
    )

    val result = runner.runWithDataWithType[java.util.Map[String, Long], Row](
      scenario,
      inputValues,
      inputType,
      watermarkStrategy = Some(
        WatermarkStrategyUtils.afterEachEvent(
          (input, _: Long) => input.get("timestamp"),
          Duration.ZERO
        )
      )
    )

    result.validValue.successes shouldBe List(
      Map("num" -> 1L, "running_sum" -> 1L).toRow,
      Map("num" -> 2L, "running_sum" -> 3L).toRow,
      Map("num" -> 3L, "running_sum" -> 6L).toRow,
    )
  }

  test("should allow Window Top-N") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" ->
          """|SELECT input AS top_value
             |FROM (
             |  SELECT
             |    input,
             |    ROW_NUMBER() OVER (
             |      PARTITION BY window_start, window_end
             |      ORDER BY input DESC
             |    ) AS top_n_position
             |  FROM TABLE(
             |    TUMBLE(TABLE record, DESCRIPTOR(record_time), INTERVAL '1' DAY)
             |  )
             |)
             |WHERE top_n_position <= 2
             |""".stripMargin.spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithData[Int, Row](
      scenario,
      List(1, 5, 3, 2)
    )

    result.validValue.successes shouldBe List(
      Map("top_value" -> 5).toRow,
      Map("top_value" -> 3).toRow,
    )
  }

  test("should propagate source watermark and drop late events in event-time window") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" ->
          """|SELECT
             |  window_start,
             |  window_end,
             |  ARRAY_AGG(input) AS `values`
             |FROM TABLE(
             |  TUMBLE(TABLE record, DESCRIPTOR(record_time), INTERVAL '1' SECOND)
             |)
             |GROUP BY window_start, window_end
             |""".stripMargin.spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithData[Int, Row](
      scenario,
      List(1000, 1500, 2500, 2000, 900), // 900 is rejected as beyond 1 second out-of-orderness
      watermarkStrategy = Some(
        WatermarkStrategyUtils.afterEachEvent[Int](
          (value: Int, _: Long) => value.toLong,
          Duration.ofSeconds(1)
        )
      )
    )
    result.validValue.successes shouldBe List(
      Map(
        "window_start" -> LocalDateTime.ofInstant(Instant.ofEpochMilli(1000), ZoneId.systemDefault),
        "window_end"   -> LocalDateTime.ofInstant(Instant.ofEpochMilli(2000), ZoneId.systemDefault),
        "values"       -> Array[Integer](1000, 1500)
      ).toRow,
      Map(
        "window_start" -> LocalDateTime.ofInstant(Instant.ofEpochMilli(2000), ZoneId.systemDefault),
        "window_end"   -> LocalDateTime.ofInstant(Instant.ofEpochMilli(3000), ZoneId.systemDefault),
        "values"       -> Array[Integer](2500, 2000)
      ).toRow,
    )
  }

  test("should allow pattern recognition with MATCH_RECOGNIZE") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" ->
          """|SELECT first_val, second_val, third_val
             |FROM (
             |  SELECT input.num AS num, record_time
             |  FROM record
             |)
             |MATCH_RECOGNIZE (
             |  ORDER BY record_time, num
             |  MEASURES
             |    A.num AS first_val,
             |    B.num AS second_val,
             |    C.num AS third_val
             |  ONE ROW PER MATCH
             |  AFTER MATCH SKIP PAST LAST ROW
             |  PATTERN (A B C)
             |  DEFINE
             |    A AS TRUE,
             |    B AS B.num > A.num,
             |    C AS C.num > B.num
             |)
             |""".stripMargin.spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val inputType = Typed.record(List("num" -> Typed[Long], "timestamp" -> Typed[Long]))
    val inputValues = List(
      Map("num" -> 1L, "timestamp" -> 1L).asJava,
      Map("num" -> 2L, "timestamp" -> 2L).asJava,
      Map("num" -> 3L, "timestamp" -> 3L).asJava,
      Map("num" -> 0L, "timestamp" -> 4L).asJava,
      Map("num" -> 0L, "timestamp" -> 5L).asJava
    )

    val result = runner.runWithDataWithType[java.util.Map[String, Long], Row](
      scenario,
      inputValues,
      inputType,
      watermarkStrategy = Some(
        WatermarkStrategyUtils.afterEachEvent(
          (input, _: Long) => input.get("timestamp"),
          Duration.ZERO
        )
      )
    )

    result.validValue.successes shouldBe List(
      Map("first_val" -> 1L, "second_val" -> 2L, "third_val" -> 3L).toRow
    )
  }

  test("simple sql select should handle all types") {
    case class SameInputOutputTypeCase(name: String, typ: TypingResult, value: Any)
    case class DifferentInputOutputTypeCase(name: String, typ: TypingResult, input: Any, expected: Any)
    case class TypeCase(columnName: String, typ: TypingResult, input: Any, expected: Any)

    val sameInputOutputCases = List(
      SameInputOutputTypeCase("stringValue", Typed[String], "sample"),
      SameInputOutputTypeCase("booleanValue", Typed[Boolean], true),
      SameInputOutputTypeCase("byteValue", Typed[Byte], 7.toByte),
      SameInputOutputTypeCase("shortValue", Typed[Short], 123.toShort),
      SameInputOutputTypeCase("intValue", Typed[Int], 123456),
      SameInputOutputTypeCase("longValue", Typed[Long], 1234567890L),
      SameInputOutputTypeCase("floatValue", Typed[Float], 1.25f),
      SameInputOutputTypeCase("doubleValue", Typed[Double], 2.5d),
      SameInputOutputTypeCase(
        "bigIntegerValue",
        Typed[java.math.BigInteger],
        new java.math.BigInteger("12345678901234567890")
      ),
      SameInputOutputTypeCase("localDateValue", Typed[LocalDate], LocalDate.parse("2026-01-01")),
      SameInputOutputTypeCase("localTimeValue", Typed[LocalTime], LocalTime.parse("12:00:00.123")),
      SameInputOutputTypeCase(
        "localDateTimeValue",
        Typed[LocalDateTime],
        LocalDateTime.parse("2026-01-01T12:00:00.123")
      ),
      SameInputOutputTypeCase("instantValue", Typed[Instant], Instant.parse("2026-01-01T12:00:00.123Z")),
      SameInputOutputTypeCase(
        "offsetDateTimeValue",
        Typed[OffsetDateTime],
        OffsetDateTime.parse("2026-01-01T12:00:00.123+01:00")
      ),
      SameInputOutputTypeCase(
        "zonedDateTimeValue",
        Typed[ZonedDateTime],
        ZonedDateTime.parse("2026-01-01T12:00:00.123+01:00[Europe/Warsaw]")
      ),
      SameInputOutputTypeCase("durationValue", Typed[Duration], Duration.parse("PT2H3M4S")),
      SameInputOutputTypeCase("uuidValue", Typed[UUID], UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
      SameInputOutputTypeCase(
        "mapValue",
        Typed.fromDetailedType[java.util.Map[String, Long]],
        Map("k1" -> 1L, "k2" -> 2L).asJava
      ),
    )

    val differentInputOutputCases = List(
      DifferentInputOutputTypeCase(
        "bigDecimalValue",
        Typed[java.math.BigDecimal],
        new java.math.BigDecimal("12345.6789"),
        new java.math.BigDecimal("12345.678900000000000000")
      ),
      DifferentInputOutputTypeCase(
        "listValue",
        Typed.fromDetailedType[java.util.List[String]],
        List("a", "b", "c").asJava,
        List("a", "b", "c").toArray
      ),
    )

    val allTypesCases = sameInputOutputCases.map(c => TypeCase(c.name, c.typ, c.value, c.value)) ++
      differentInputOutputCases.map(c => TypeCase(c.name, c.typ, c.input, c.expected))

    val inputRecordType  = Typed.record(allTypesCases.map(c => c.columnName -> c.typ))
    val inputRecordValue = allTypesCases.map(c => c.columnName -> c.input).toMap

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .customNode(
        id = "flink-sql-query",
        outputVar = "out",
        customNodeRef = "flink-sql-query",
        "flinkSqlQuery" -> s"SELECT input.* FROM record".spelTemplate
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#out".spel)

    val result = runner.runWithDataWithType[java.util.Map[String, Any], Row](
      scenario,
      List(inputRecordValue.asJava),
      inputRecordType
    )

    val outputRow = result.validValue.successes.loneElement
    allTypesCases.foreach { testCase =>
      withClue(s"Field '${testCase.columnName}' mismatch: ") {
        outputRow.getField(testCase.columnName) shouldBe testCase.expected
      }
    }
  }

  private implicit class MapToRowOps(map: Map[String, Any]) {

    def toRow: Row = {
      val row = Row.withNames()
      map.foreach { case (fieldName, fieldValue) => row.setField(fieldName, fieldValue.asInstanceOf[AnyRef]) }
      row
    }

  }

}
