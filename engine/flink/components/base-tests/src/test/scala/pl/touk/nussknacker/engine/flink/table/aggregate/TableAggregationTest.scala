package pl.touk.nussknacker.engine.flink.table.aggregate

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.table.api.ValidationException
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.TestTableComponents._
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationTest.TestRecord
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class TableAggregationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] =
    singleRecordBatchTable :: FlinkTableComponentProvider.configIndependentComponents ::: Nil

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(additionalComponents)
    .build()

  // As of Flink 1.19, time-related types are not supported in FIRST_VALUE aggregate function.
  // See: https://issues.apache.org/jira/browse/FLINK-15867
  // See AggFunctionFactory.createFirstValueAggFunction
  test("should be able to aggregate by number types, string and boolean") {
    val aggregatingBranches =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions).zipWithIndex.map {
        case (expr, i) =>
          aggregationTypeTestingBranch(groupByExpr = spelStr.spel, aggregateByExpr = expr.spel, idSuffix = i.toString)
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

  test("should be able to group by simple types") {
    val aggregatingBranches =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions ::: tableApiSupportedTimeLiteralExpressions).zipWithIndex
        .map { case (expr, i) =>
          aggregationTypeTestingBranch(groupByExpr = expr.spel, aggregateByExpr = spelStr.spel, idSuffix = i.toString)
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
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
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
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
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

}

object TableAggregationTest {
  case class TestRecord(someKey: String, someAmount: Int)
}
