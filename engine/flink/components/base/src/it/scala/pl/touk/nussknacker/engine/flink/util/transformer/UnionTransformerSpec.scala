package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.NuTestScenarioRunner
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class UnionTransformerSpec extends FunSuite with BeforeAndAfterEach with Matchers with FlinkSpec with LazyLogging with VeryPatientScalaFutures {

  import spel.Implicits._

  private val BranchFooId = "foo"

  private val BranchBarId = "bar"

  private val UnionNodeId = "joined-node-id"

  private val OutVariableName = "outVar"

  val data = List("10", "20", "30", "40")

  override protected def afterEach(): Unit = {
    MockService.clear()
  }

  test("should unify streams with union-memo") {
    val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

    val scenario = EspProcess(MetaData("sample-union-memo", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "noopSource")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union-memo", Some(OutVariableName),
          List(
            BranchFooId -> List("key" -> "'fooKey'", "value" -> "#input"),
            BranchBarId -> List("key" -> "'barKey'", "value" -> "#input")
          ), "stateTimeout" -> "T(java.time.Duration).parse('PT1M')"
        )
        .processorEnd("end", "invocationCollector", "value" -> s"#$OutVariableName.$BranchFooId")
    ))

    val results = testScenarioRunner.runWithData(scenario, data)
    results.map(_.successes) shouldBe Valid(data)
  }

  test("should unify streams with union when one branch is empty") {
    val testScenarioRunner = NuTestScenarioRunner.flinkBased(config, flinkMiniCluster).build()

    val scenario = EspProcess(MetaData("sample-union", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "noopSource")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union", Some(OutVariableName),
          List(
            BranchFooId -> List("Output expression" -> "{a: #input}"),
            BranchBarId -> List("Output expression" -> "{a: '123'}"))
        )
        .processorEnd("end", "invocationCollector", "value" -> s"#$OutVariableName.a")
    ))

    val results = testScenarioRunner.runWithData(scenario, data)
    results.map(_.successes) shouldBe Valid(data)
  }

  test("should unify streams with union when both branches emit data") {
    val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

    val scenario = EspProcess(MetaData("sample-union", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "source")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union", Some(OutVariableName),
          List(
            BranchFooId -> List("Output expression" -> "{a: #input}"),
            BranchBarId -> List("Output expression" -> "{a: '123'}"))
        )
        .processorEnd("end", "invocationCollector", "value" -> s"#$OutVariableName.a")
    ))

    val results = testScenarioRunner.runWithData(scenario, data)

    results.map(_.successes.size) shouldBe Valid(data.size * 2)
    results.map(_.successes.toSet) shouldBe Valid(data.toSet + "123")
  }

  test("should throw when contexts are different") {
    val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

    val scenario = EspProcess(MetaData("sample-union", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "noopSource")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union", Some(OutVariableName),
          List(
            BranchFooId -> List("Output expression" -> "{a: #input}"),
            BranchBarId -> List("Output expression" -> "{b: 123}")
          )
        )
        .processorEnd("end", "invocationCollector", "value" -> s"#$OutVariableName.a")
    ))

    intercept[IllegalArgumentException] {
      testScenarioRunner.runWithData(scenario, data)
    }.getMessage should include("All branch values must be of the same")
  }

  test("should not throw when one branch emits error") {
    val data = List(10, 20, 30, 40)
    val testScenarioRunner = NuTestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

    val scenario = EspProcess(MetaData("sample-union", StreamMetaData()), NonEmptyList.of[SourceNode](
      GraphBuilder.source("start-foo", "source")
        .branchEnd(BranchFooId, UnionNodeId),
      GraphBuilder.source("start-bar", "source")
        .branchEnd(BranchBarId, UnionNodeId),
      GraphBuilder
        .join(UnionNodeId, "union", Some(OutVariableName),
          List(
            BranchFooId -> List("Output expression" -> "#input"),
            BranchBarId -> List("Output expression" -> "#input / (#input % 4)")
          )
        )
        .processorEnd("end", "invocationCollector", "value" -> s"#$OutVariableName")
    ))



    val results = testScenarioRunner.runWithData(scenario, data)

    results.map(_.successes.size) shouldBe Valid(6)
    results.map(_.successes.toSet) shouldBe Valid(Set(5, 10, 15, 20, 30, 40))
  }
}
