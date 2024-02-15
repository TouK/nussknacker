package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.defaultmodel.SerializationTest.DataStructureWithOptionals
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage._

class SerializationTest extends FlinkWithKafkaSuite with LazyLogging {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import spel.Implicits._

  test("some serialization test") {
    val scenario = ScenarioBuilder
      .streaming("serialization-test")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .processorEnd("end", TestScenarioRunner.testResultService, "value" -> "#input.field2")
    val testScenarioRunner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
      .build()
    val result =
      testScenarioRunner.runWithData(scenario, List(DataStructureWithOptionals("firstField", Option("optionalField"))))
    result.validValue shouldBe RunResult.success("optionalField")
  }

}

object SerializationTest {
  final case class DataStructureWithOptionals(field1: String, field2: Option[String])
}
