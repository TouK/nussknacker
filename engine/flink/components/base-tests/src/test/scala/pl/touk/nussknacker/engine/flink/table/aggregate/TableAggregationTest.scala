package pl.touk.nussknacker.engine.flink.table.aggregate

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.{BeforeAndAfterAll, Inside}
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.SpelValues._
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationTest.{
  buildMultipleAggregationsScenario,
  AggregationParameters,
  TestRecord
}
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.math.BigInteger
import java.time.{LocalDate, OffsetDateTime, ZonedDateTime}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

class TableAggregationTest
    extends AnyFunSuite
    with TableDrivenPropertyChecks
    with Matchers
    with Inside
    with BeforeAndAfterAll {

  private lazy val additionalComponents: List[ComponentDefinition] =
    FlinkTableComponentProvider.configIndependentComponents

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(additionalComponents)
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("should be able to aggregate by number types, string and boolean declared in spel") {
    val aggregationParameters =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions).map { expr =>
        AggregationParameters(aggregator = "'First'".spel, aggregateBy = expr, groupBy = spelStr)
      }
    val scenario = buildMultipleAggregationsScenario(aggregationParameters)
    val result   = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.successes.size shouldBe aggregationParameters.size
  }

  test("should be able to group by simple types declared in spel") {
    val aggregationParameters =
      (spelBoolean :: spelStr :: spelBigDecimal :: numberPrimitiveLiteralExpressions ::: tableApiSupportedTimeLiteralExpressions)
        .map { expr =>
          AggregationParameters("'First'".spel, spelStr, expr)
        }

    val scenario = buildMultipleAggregationsScenario(aggregationParameters)
    val result   = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result.validValue.successes.size shouldBe aggregationParameters.size
  }

  test("should be able to group by advanced types") {
    val aggregationParameters = ("{foo: 1}".spel :: "{{foo: 1, bar: '123'}}".spel :: Nil)
      .map { expr => AggregationParameters("'First'".spel, spelStr, expr) }
    val scenario = buildMultipleAggregationsScenario(aggregationParameters)
    val result   = runner.runWithData(scenario, List(0), Boundedness.BOUNDED)
    result shouldBe Symbol("valid")
  }

  test("reports error when grouping by a type aligned to RAW") {
    val scenario = buildMultipleAggregationsScenario(
      List(
        AggregationParameters(aggregator = "'First'".spel, aggregateBy = "''".spel, groupBy = "#input".spel)
      )
    )
    val result = runner.runWithData(
      scenario,
      List(OffsetDateTime.now()),
      Boundedness.BOUNDED
    )
    result.invalidValue.toList.loneElement should matchPattern {
      case CustomNodeError("agg0", _, Some(ParameterName("groupBy"))) =>
    }
  }

  test("aggregations should aggregate by integers") {
    val input = List(1, 1, 2)
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Average"                       -> 1,
      "Count"                         -> 3,
      "Min"                           -> 1,
      "Max"                           -> 2,
      "First"                         -> 1,
      "Last"                          -> 2,
      "Sum"                           -> 4,
      "Population standard deviation" -> 0,
      "Sample standard deviation"     -> 0,
      "Population variance"           -> 0,
      "Sample variance"               -> 0,
      "Collect"                       -> Map(1 -> 2, 2 -> 1).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
  }

  test("aggregations should aggregate by doubles") {
    val input = List(2.0, 1.0)
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Average"                       -> 1.5,
      "Count"                         -> 2,
      "Min"                           -> 1.0,
      "Max"                           -> 2.0,
      "First"                         -> 2.0,
      "Last"                          -> 1.0,
      "Sum"                           -> 3.0,
      "Population standard deviation" -> 0.5,
      "Sample standard deviation"     -> 0.7071067811865476,
      "Population variance"           -> 0.25,
      "Sample variance"               -> 0.5,
      "Collect"                       -> Map(2.0 -> 1, 1.0 -> 1).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
  }

  test("aggregations should aggregate by strings") {
    val input = List("def", "abc")
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Count"   -> 2,
      "Min"     -> "abc",
      "Max"     -> "def",
      "First"   -> "def",
      "Last"    -> "abc",
      "Collect" -> Map("def" -> 1, "abc" -> 1).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
  }

  test("aggregations should aggregate by big decimals with Flink and return results in default scale (18)") {
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
      "Sample variance"               -> java.math.BigDecimal.valueOf(0.5).setScale(18),
      "Collect" -> Map(
        java.math.BigDecimal.valueOf(1).setScale(18) -> 1,
        java.math.BigDecimal.valueOf(2).setScale(18) -> 1
      ).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
  }

  test("max, min and count aggregations should aggregate by date types") {
    val input = List(LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-02"))
    val aggregatorWithExpectedResult = List(
      "Count"   -> 2,
      "Min"     -> LocalDate.parse("2000-01-01"),
      "Max"     -> LocalDate.parse("2000-01-02"),
      "Collect" -> Map(LocalDate.parse("2000-01-01") -> 1, LocalDate.parse("2000-01-02") -> 1).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
  }

  test("reports error when using LocalDate in aggregateBy for aggregators that don't support time types") {
    val input = List(LocalDate.parse("2000-01-01"), LocalDate.parse("2000-01-02"))
    val aggregatorsWithoutTimeTypesSupport = List(
      "Average",
      "First",
      "Last",
      "Sum",
      "Population standard deviation",
      "Sample standard deviation",
      "Population variance",
      "Sample variance",
    )
    val scenarios = aggregatorsWithoutTimeTypesSupport.map(a =>
      buildMultipleAggregationsScenario(
        List(
          AggregationParameters(aggregator = s"'$a'".spel, aggregateBy = "#input".spel, groupBy = "''".spel)
        )
      )
    )
    scenarios.foreach(s => {
      val result = runner.runWithData(s, input, Boundedness.BOUNDED)
      result.invalidValue.toList.loneElement should matchPattern {
        case CustomNodeError("agg0", _, Some(ParameterName("aggregateBy"))) =>
      }
    })
  }

  test("reports error when aggregating by type aligned to RAW") {
    val scenario = buildMultipleAggregationsScenario(
      List(
        AggregationParameters(aggregator = "'First'".spel, aggregateBy = "#input".spel, groupBy = "''".spel)
      )
    )
    val results = List(
      runner.runWithData(scenario, List(OffsetDateTime.now()), Boundedness.BOUNDED),
      runner.runWithData(scenario, List(ZonedDateTime.now()), Boundedness.BOUNDED),
      runner.runWithData(scenario, List(BigInteger.ONE), Boundedness.BOUNDED)
    )
    results.foreach { r =>
      r.invalidValue.toList.loneElement should matchPattern {
        case CustomNodeError("agg0", _, Some(ParameterName("aggregateBy"))) =>
      }
    }
  }

  test("count and collect aggregation works when aggregating by type aligned to RAW") {
    val input =
      List(OffsetDateTime.parse("2024-01-01T23:59:30+03:00"), OffsetDateTime.parse("2024-01-02T23:59:30+04:00"))
    val aggregatorWithExpectedResult: List[AggregateByInputTestData] = List(
      "Count" -> 2,
      "Collect" -> Map(
        OffsetDateTime.parse("2024-01-01T23:59:30+03:00") -> 1,
        OffsetDateTime.parse("2024-01-02T23:59:30+04:00") -> 1
      ).asJava
    ).map(a => AggregateByInputTestData(a._1, a._2))
    runMultipleAggregationTest(input, aggregatorWithExpectedResult)
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

  private def runMultipleAggregationTest[T: ClassTag](
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
    result.validValue.successes.map(r => r.asScala.toMap).toSet shouldBe expectedResults.toSet
  }

}

object TableAggregationTest extends AnyFunSuite {
  case class TestRecord(someKey: String, someAmount: Int)

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
