package pl.touk.nussknacker.devmodel

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.devmodel.TableAggregationTest.TestRecord
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableAggregationComponentProvider
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregation
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class TableAggregationTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.Implicits._

  import scala.jdk.CollectionConverters._

  test("should emit groupBy key and aggregated values as separate variables") {
    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .withExtraComponents(FlinkTableAggregationComponentProvider.components)
      .build()

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", TestScenarioRunner.testDataSource)
      .customNode(
        "aggregate",
        "agg",
        "aggregate",
        TableAggregation.groupByParamName.value            -> "#input.someKey",
        TableAggregation.aggregateByParamName.value        -> "#input.someAmount",
        TableAggregation.aggregatorFunctionParamName.value -> "'Sum'",
      )
      .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "{#key, #agg}")

    val result = runner.runWithDataInBoundedMode(
      scenario,
      List(
        TestRecord("A", 1),
        TestRecord("B", 2),
        TestRecord("A", 1),
        TestRecord("B", 2),
      )
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
