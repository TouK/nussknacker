package pl.touk.nussknacker.engine.flink.table.aggregate

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.table.api.ValidationException
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory.aggregateByParamName
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationTest.TestRecord
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable
import org.scalatest.LoneElement._
import pl.touk.nussknacker.engine.api.parameter.ParameterName

import java.math.BigInteger
import java.time.{LocalDate, OffsetDateTime, ZonedDateTime}
import scala.reflect.ClassTag

class TableAggregationTest extends AnyFunSuite with TableDrivenPropertyChecks with FlinkSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] =
    FlinkTableComponentProvider.configIndependentComponents

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(additionalComponents)
    .build()

  test("first value aggregator should be able to aggregate by number types, string and boolean declared in spel") {
    val aggregationParameters =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions).map { expr =>
        AggregationParameters(aggregator = "'First'".spel, aggregateBy = expr.spel, groupBy = spelStr.spel)
      }
    val scenario = buildMultipleAggregationsScenario(aggregationParameters)
    // TODO: add a `runWithSingleRecordBoundedSource` to runner
    val result = runner.runWithData(
      scenario,
      List(1),
      Boundedness.BOUNDED
    )
    result shouldBe Symbol("valid")
    result.validValue.successes.size shouldBe aggregationParameters.size
  }

  test("first value aggregator should be able to group by simple types declared in spel") {
    val aggregationParameters =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions ::: tableApiSupportedTimeLiteralExpressions)
        .map { expr =>
          AggregationParameters("'First'".spel, spelStr.spel, expr.spel)
        }

    val scenario = buildMultipleAggregationsScenario(aggregationParameters)
    val result = runner.runWithData(
      scenario,
      List(1),
      Boundedness.BOUNDED
    )
    result shouldBe Symbol("valid")
    result.validValue.successes.size shouldBe aggregationParameters.size
  }

  test("throws exception when using not supported types in aggregate in groupBy") {
    val scenario = buildMultipleAggregationsScenario(
      List(
        AggregationParameters(aggregator = "'Min'".spel, aggregateBy = "''".spel, groupBy = "#input".spel)
      )
    )
    // TODO: add more cases
    // TODO: change this behavior to validate this at compile time
    assertThrows[ValidationException] {
      runner.runWithData(
        scenario,
        List(OffsetDateTime.now()),
        Boundedness.BOUNDED
      )
    }
  }

  test("throws exception when using not supported types in aggregate in aggregateBy") {
    val scenario = buildMultipleAggregationsScenario(
      List(
        AggregationParameters(aggregator = "'Min'".spel, aggregateBy = "#input".spel, groupBy = "''".spel)
      )
    )
    def assertNodeError[T: ClassTag](inputs: List[T]): Unit = {
      val result = runner.runWithData(
        scenario,
        inputs,
        Boundedness.BOUNDED
      )
      // TODO: assert error content to be sure
      result.invalidValue.head should matchPattern {
        case CustomNodeError(_, _, Some(paramName)) if paramName == aggregateByParamName =>
      }
    }
    // TODO: add all cases
    assertNodeError(List(ZonedDateTime.now()))
    assertNodeError(List(OffsetDateTime.now()))
    assertNodeError(List(BigInteger.ONE))
  }

  test("reports error when using not supported types in aggregateBy") {
    val scenario = buildMultipleAggregationsScenario(
      List(
        AggregationParameters(aggregator = "'Min'".spel, aggregateBy = "#input".spel, groupBy = "''".spel)
      )
    )
    def assertNodeError[T: ClassTag](inputs: List[T], expectedError: ProcessCompilationError): Unit = {
      val result = runner.runWithData(
        scenario,
        inputs,
        Boundedness.BOUNDED
      )
      result.invalidValue.toList.loneElement shouldBe expectedError
    }
    // TODO: add all cases
    assertNodeError(
      List(ZonedDateTime.now()),
      CustomNodeError("Invalid type: ZonedDateTime", Some(ParameterName("aggregateBy")))(NodeId("agg1"))
    )
//    assertNodeError(List(OffsetDateTime.now()), CustomNodeError())
//    assertNodeError(List(BigInteger.ONE), CustomNodeError())
  }

  test("aggregations should aggregate by integers") {
    val input = List(1, 2)
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Average"                       -> 1,
      "Count"                         -> 2,
      "Min"                           -> 1,
      "Max"                           -> 2,
      "First"                         -> 1,
      "Last"                          -> 2,
      "Sum"                           -> 3,
      "Population standard deviation" -> 0,
      "Sample standard deviation"     -> 1,
      "Population variance"           -> 0,
      "Sample variance"               -> 1,
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runAggregateByInputTest(input, aggregatorWithExpectedResult)
  }

  test("aggregations should aggregate by doubles") {
    val input = List(1.0, 2.0)
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Average"                       -> 1.5,
      "Count"                         -> 2,
      "Min"                           -> 1.0,
      "Max"                           -> 2.0,
      "First"                         -> 1.0,
      "Last"                          -> 2.0,
      "Sum"                           -> 3.0,
      "Population standard deviation" -> 0.5,
      "Sample standard deviation"     -> 0.7071067811865476,
      "Population variance"           -> 0.25,
      "Sample variance"               -> 0.5
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runAggregateByInputTest(input, aggregatorWithExpectedResult)
  }

  test("aggregations should aggregate by big decimals with Flink default scale (18)") {
    val input = List(java.math.BigDecimal.valueOf(1), java.math.BigDecimal.valueOf(2))
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Average"                       -> java.math.BigDecimal.valueOf(1.5).setScale(18),
      "Count"                         -> 2,
      "Min"                           -> java.math.BigDecimal.valueOf(1.0).setScale(18),
      "Max"                           -> java.math.BigDecimal.valueOf(2.0).setScale(18),
      "First"                         -> java.math.BigDecimal.valueOf(1.0).setScale(18),
      "Last"                          -> java.math.BigDecimal.valueOf(2.0).setScale(18),
      "Sum"                           -> java.math.BigDecimal.valueOf(3.0).setScale(18),
      "Population standard deviation" -> java.math.BigDecimal.valueOf(0.5).setScale(18),
      "Sample standard deviation"     -> java.math.BigDecimal.valueOf(0.7071067811865476).setScale(18),
      "Population variance"           -> java.math.BigDecimal.valueOf(0.25).setScale(18),
      "Sample variance"               -> java.math.BigDecimal.valueOf(0.5).setScale(18)
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runAggregateByInputTest(input, aggregatorWithExpectedResult)
  }

  // TODO: add more date types
  test("max, min and count aggregations should aggregate by date types") {
    val input = List(LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-02"))
    val aggregatorWithExpectedResult = List(
//      "Average" -> 1,
      "Count" -> 2,
      "Min"   -> LocalDate.parse("2000-01-01"),
      "Max"   -> LocalDate.parse("2000-01-02"),
//      "First" -> LocalDate.parse("2000-01-01"),
//      "Last" -> LocalDate.parse("2000-01-01"),
//      "Sum"   -> 3,
//      "Population standard deviation" -> 0,
//      "Sample standard deviation" -> 1,
//      "Population variance" -> 0,
//      "Sample variance" -> 1,
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runAggregateByInputTest(input, aggregatorWithExpectedResult)
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

  case class AggregateByInputTestData(aggregator: String, expectedResult: Any)

  private def runAggregateByInputTest[T: ClassTag](
      input: List[T],
      aggregatorWithExpectedResult: List[AggregateByInputTestData]
  ) = {
    val aggregationsParams = aggregatorWithExpectedResult.map(a => {
      val aggregatorName = s"'${a.aggregator}'".spel
      AggregationParameters(aggregator = aggregatorName, aggregateBy = "#input".spel, groupBy = aggregatorName)
    })
    val expectedResults =
      aggregatorWithExpectedResult.map(a => Map("key" -> a.aggregator, "result" -> a.expectedResult))
    val scenario = buildMultipleAggregationsScenario(aggregationsParams)
    val result = runner.runWithData[T, java.util.LinkedHashMap[String, AnyRef]](
      scenario,
      input,
      Boundedness.BOUNDED
    )
    println(result)
    result.validValue.successes.map(r => r.asScala.toMap).toSet shouldBe expectedResults.toSet
  }

  case class AggregationParameters(aggregator: Expression, aggregateBy: Expression, groupBy: Expression)

  private def buildMultipleAggregationsScenario(
      aggregationParameters: List[AggregationParameters]
  ): CanonicalProcess = {
    val aggregationsBranches = aggregationParameters.zipWithIndex.map { case (params, i) =>
      GraphBuilder
        .customNode(
          id = s"agg$i",
          outputVar = "agg",
          customNodeRef = "aggregate",
          "groupBy"     -> params.groupBy,
          "aggregateBy" -> params.aggregateBy,
          "aggregator"  -> params.aggregator
        )
        .emptySink(s"end$i", TestScenarioRunner.testResultSink, "value" -> "{key: #key, result: #agg}".spel)
    }
    ScenarioBuilder
      .streaming("test")
      .sources(
        GraphBuilder
          .source("source", TestScenarioRunner.testDataSource)
          .split(
            "split",
            aggregationsBranches: _*
          )
      )
  }

}

object TableAggregationTest {
  case class TestRecord(someKey: String, someAmount: Int)
}
