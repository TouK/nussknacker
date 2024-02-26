package pl.touk.nussknacker.devmodel

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}

class TableApiHardcodedSourceTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  import scala.jdk.CollectionConverters._
  import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable
  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._

  val sinkId       = "sinkId"
  val sourceId     = "sourceId"
  val resultNodeId = "resultVar"
  val filterId     = "filter"

  test("should produce results for each element in list") {
    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .withExtraComponents(TableComponentProvider.ConfigIndependentComponents)
      .build()

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(sourceId, "tableApi-source-hardcoded")
      .filter(filterId, "#input.someInt != 1")
      .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#input")

    val expectedMap: java.util.Map[String, Any] = Map("someString" -> "BBB", "someInt" -> 2).asJava

    runner.runWithoutData(scenario).validValue shouldBe RunResult.success(expectedMap)
  }

}
