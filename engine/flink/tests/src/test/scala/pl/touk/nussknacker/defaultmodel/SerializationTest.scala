package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.SerializationTest.DataStructureWithOptionals
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage._

class SerializationTest extends AnyFunSuite with Matchers with LazyLogging with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val testScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("some serialization test") {
    val scenario = ScenarioBuilder
      .streaming("serialization-test")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.field2".spel)
    val result =
      testScenarioRunner.runWithData(scenario, List(DataStructureWithOptionals("firstField", Option("optionalField"))))
    result.validValue shouldBe RunResult.success("optionalField")
  }

}

object SerializationTest {
  final case class DataStructureWithOptionals(field1: String, field2: Option[String])
}
