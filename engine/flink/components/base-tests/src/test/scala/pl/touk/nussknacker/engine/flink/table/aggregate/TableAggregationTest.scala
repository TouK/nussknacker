package pl.touk.nussknacker.engine.flink.table.aggregate

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.table.api.ValidationException
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.TestTableComponents._
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationTest.TestRecord
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.time.LocalDate
import scala.reflect.ClassTag

class TableAggregationTest extends AnyFunSuite with TableDrivenPropertyChecks with FlinkSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] =
    singleRecordBatchTable :: FlinkTableComponentProvider.configIndependentComponents ::: Nil

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(additionalComponents)
    .build()

  // As of Flink 1.19, time-related types are not supported in FIRST_VALUE aggregate function.
  // See: https://issues.apache.org/jira/browse/FLINK-15867
  // See AggFunctionFactory.createFirstValueAggFunction
  test("should be able to aggregate by number types, string and boolean") {
    val aggregatingBranches =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions).zipWithIndex.map {
        case (expr, branchIndex) =>
          aggregationTypeTestingBranch(
            groupByExpr = spelStr.spel,
            aggregateByExpr = expr.spel,
            idSuffix = branchIndex.toString
          )
      }

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .split(
        "split",
        aggregatingBranches: _*
      )

    val result = runner.runWithoutData(scenario)
    result shouldBe Symbol("valid")
  }

  // TODO: make this test check output value
  test("should be able to group by simple types") {
    val aggregatingBranches =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions ::: tableApiSupportedTimeLiteralExpressions).zipWithIndex
        .map { case (expr, branchIndex) =>
          aggregationTypeTestingBranch(
            groupByExpr = expr.spel,
            aggregateByExpr = spelStr.spel,
            idSuffix = branchIndex.toString
          )
        }

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .split(
        "split",
        aggregatingBranches: _*
      )

    val result = runner.runWithoutData(scenario)
    result.isValid shouldBe true
  }

  // TODO: remove when Flink Table API adds support for OffsetDateTime
  test("throws exception when using not supported OffsetDateTime in aggregate") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", oneRecordTableSourceName, "Table" -> s"'$oneRecordTableName'".spel)
      .to(
        aggregationTypeTestingBranch(
          groupByExpr = spelOffsetDateTime.spel,
          aggregateByExpr = spelStr.spel,
          idSuffix = ""
        )
      )

    assertThrows[ValidationException] {
      runner.runWithoutData(scenario)
    }
  }

  // TODO: add all cases
  test("should not validate types that dont work on table aggregations") {
    val invalidSumCases = Table(
      ("aggregateByInput", "aggregator"),
      (List(java.math.BigInteger.ONE, java.math.BigInteger.TEN), "'Sum'".spel),
    )
    forAll(invalidSumCases) {
      case (input, aggregator) => {
        val result = runBatchAggregationScenario(input, aggregator)
        result.invalidValue.head should matchPattern {
          case CustomNodeError(_, _, Some(paramName)) if paramName == aggregateByParamName =>
        }
      }
    }
  }

  test("should do aggregations correctly on integers") {
    forAll(
      Table(
        ("aggregateByInput", "aggregator", "result"),
        (List(1, 2), "'Sum'".spel, 3),
        (List(1, 2), "'Average'".spel, 1),
        (List(1, 2), "'First'".spel, 1),
        (List(1, 2), "'Last'".spel, 2),
        (List(1, 2), "'Min'".spel, 1),
        (List(1, 2), "'Max'".spel, 2),
        (List(1, 4), "'Population standard deviation'".spel, 1),
        (List(1, 4), "'Sample standard deviation'".spel, 2),
        (List(1, 4), "'Population variance'".spel, 2),
        (List(1, 4), "'Sample variance'".spel, 5),
      )
    ) { case (input, aggregator, expectedResult) =>
      val result = runBatchAggregationScenario(input, aggregator)
      result.validValue.successes shouldBe expectedResult :: Nil
    }
  }

  test("should do aggregations correctly on doubles") {
    forAll(
      Table(
        ("aggregateByInput", "aggregator", "result"),
        (List(1.1, 2.2), "'Sum'".spel, 3.3000000000000003), // Floating-point arithmetic related error
        (List(1.1, 2.2), "'Average'".spel, 1.6500000000000001),
        (List(1.1, 2.2), "'First'".spel, 1.1),
        (List(1.1, 2.2), "'Last'".spel, 2.2),
        (List(1.1, 2.2), "'Min'".spel, 1.1),
        (List(1.1, 2.2), "'Max'".spel, 2.2),
        (List(1.1, 2.2), "'Population standard deviation'".spel, 0.5499999999999998),
        (List(1.1, 2.2), "'Sample standard deviation'".spel, 0.777817459305202),
        (List(1.1, 2.2), "'Population variance'".spel, 0.30249999999999977),
        (List(1.1, 2.2), "'Sample variance'".spel, 0.6049999999999995),
      )
    ) { case (input, aggregator, expectedResult) =>
      val result = runBatchAggregationScenario(input, aggregator)
      result.validValue.successes shouldBe expectedResult :: Nil
    }
  }

  test("should do aggregations correctly on local date") {
    forAll(
      Table(
        ("aggregateByInput", "aggregator", "result"),
        (
          List(LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-02")),
          "'Min'".spel,
          LocalDate.parse("2000-01-01")
        ),
        (
          List(LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-02")),
          "'Max'".spel,
          LocalDate.parse("2000-01-02")
        )
      )
    ) { case (input, aggregator, expectedResult) =>
      val result = runBatchAggregationScenario(input, aggregator)
      result.validValue.successes shouldBe expectedResult :: Nil
    }
  }

  test("should use Flink default scale (18) for big decimal") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .customNode(
        id = "aggregate",
        outputVar = "agg",
        customNodeRef = "aggregate",
        "groupBy"     -> "'strKey'".spel,
        "aggregateBy" -> "#input".spel,
        "aggregator"  -> "'First'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#agg".spel)

    val decimal = java.math.BigDecimal.valueOf(0.123456789)
    val result = runner.runWithData(
      scenario,
      List(decimal),
      Boundedness.BOUNDED
    )

    val decimalWithAlignedScale = java.math.BigDecimal.valueOf(0.123456789).setScale(18)
    result.validValue.successes shouldBe decimalWithAlignedScale :: Nil
  }

  test("table aggregation should emit groupBy key and aggregated values as separate variables") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .customNode(
        "aggregate",
        "agg",
        "aggregate",
        TableAggregationFactory.groupByParamName.value            -> "#input.someKey".spel,
        TableAggregationFactory.aggregateByParamName.value        -> "#input.someAmount".spel,
        TableAggregationFactory.aggregatorFunctionParamName.value -> "'Sum'".spel,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "{#key, #agg}".spel)

    val result = runner.runWithData(
      scenario,
      List(
        TestRecord("A", 1),
        TestRecord("B", 2),
        TestRecord("A", 1),
        TestRecord("B", 2),
      ),
      Boundedness.BOUNDED
    )

    result.validValue.successes.toSet shouldBe Set(
      List("B", 4).asJava,
      List("A", 2).asJava,
    )
  }

  private def aggregationTypeTestingBranch(groupByExpr: Expression, aggregateByExpr: Expression, idSuffix: String) =
    GraphBuilder
      .customNode(
        id = s"aggregate$idSuffix",
        outputVar = s"agg$idSuffix",
        customNodeRef = "aggregate",
        "groupBy"     -> groupByExpr,
        "aggregateBy" -> aggregateByExpr,
        "aggregator"  -> "'First'".spel,
      )
      .emptySink(s"end$idSuffix", "dead-end")

  def runBatchAggregationScenario[T: ClassTag, R: ClassTag](
      input: List[T],
      aggregator: Expression
  ) = {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .customNode(
        id = "aggregate",
        outputVar = "agg",
        customNodeRef = "aggregate",
        "groupBy"     -> "'strKey'".spel,
        "aggregateBy" -> "#input".spel,
        "aggregator"  -> aggregator,
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#agg".spel)

    runner.runWithData[T, R](
      scenario,
      input,
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )
  }

}

object TableAggregationTest {
  case class TestRecord(someKey: String, someAmount: Int)
}
