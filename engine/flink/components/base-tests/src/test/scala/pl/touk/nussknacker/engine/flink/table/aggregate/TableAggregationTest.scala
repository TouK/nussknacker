package pl.touk.nussknacker.engine.flink.table.aggregate

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationTest.TestRecord
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.io.File

class TableAggregationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.Implicits._

  import scala.jdk.CollectionConverters._

  private lazy val tablesDefinitionFilePath = new File(
    "engine/flink/components/base-tests/src/test/resources/all-types/tables-definition.sql"
  ).toPath.toAbsolutePath

  private lazy val tableComponentProviderConfig: Config = ConfigFactory.parseString(s"""
       |{
       |  tableDefinitionFilePath: $tablesDefinitionFilePath
       |  enableFlinkBatchExecutionMode: true
       |}
       |""".stripMargin)

  private lazy val additionalComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentProviderConfig,
    ProcessObjectDependencies.withConfig(tableComponentProviderConfig)
  )

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(additionalComponents)
    .build()

  test("should be able to use all primitive types in aggregation component") {
    def aggregatingTypeTestingBranch(aggregateByExpr: Expression, idSuffix: String) = GraphBuilder
      .customNode(
        id = s"aggregate$idSuffix",
        outputVar = s"agg$idSuffix",
        customNodeRef = "aggregate",
        "groupBy"     -> aggregateByExpr,
        "aggregateBy" -> Expression.spel("#input.string"),
        "aggregator"  -> Expression.spel("'First'"),
      )
      .emptySink(s"end$idSuffix", "dead-end")

    val columnNamesExpressions: List[Expression] = List(
      "#input.string",
      "#input.boolean",
      "#input.tinyInt",
      "#input.smallInt",
      "#input.int",
      "#input.bigint",
      "#input.float",
      "#input.double",
      "#input.decimal",
      "#input.date",
      "#input.time",
      "#input.timestamp",
      "#input.timestampLtz"
    )

    val aggregatingBranches = columnNamesExpressions.zipWithIndex.map { case (expr, i) =>
      aggregatingTypeTestingBranch(expr, i.toString)
    }

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> "'all_types_input'")
      .split(
        "split",
        aggregatingBranches: _*
      )

    val result = runner.runWithData(
      scenario,
      List.empty,
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    result.isValid shouldBe true
  }

  test("table aggregation should emit groupBy key and aggregated values as separate variables") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .customNode(
        "aggregate",
        "agg",
        "aggregate",
        TableAggregationFactory.groupByParamName.value            -> "#input.someKey",
        TableAggregationFactory.aggregateByParamName.value        -> "#input.someAmount",
        TableAggregationFactory.aggregatorFunctionParamName.value -> "'Sum'",
      )
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "{#key, #agg}")

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

}

object TableAggregationTest {
  case class TestRecord(someKey: String, someAmount: Int)
}
