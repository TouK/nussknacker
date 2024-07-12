package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, VeryPatientScalaFutures}

class UnionTransformerSpec
    extends AnyFunSuite
    with BeforeAndAfterEach
    with Matchers
    with FlinkSpec
    with LazyLogging
    with VeryPatientScalaFutures {

  import ValidatedValuesDetailedMessage._
  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val BranchFooId = "foo"

  private val BranchBarId = "bar"

  private val UnionNodeId = "joined-node-id"

  private val OutVariableName = "outVar"

  private val data = List("10", "20", "30", "40")

  override def afterEach(): Unit = {
    super.afterEach()
  }

  test("should unify streams with union-memo") {
    val scenario = ScenarioBuilder
      .streaming("sample-union-memo")
      .sources(
        GraphBuilder
          .source("start-foo", TestScenarioRunner.testDataSource)
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", "noopSource")
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union-memo",
            Some(OutVariableName),
            List(
              BranchFooId -> List("key" -> "'fooKey'".spel, "value" -> "#input".spel),
              BranchBarId -> List("key" -> "'barKey'".spel, "value" -> "#input".spel)
            ),
            "stateTimeout" -> "T(java.time.Duration).parse('PT1M')".spel
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> s"#$OutVariableName.$BranchFooId".spel)
      )

    val result = testScenarioRunner.runWithData(scenario, data)
    result.validValue shouldBe RunResult.successes(data)
  }

  test("should unify streams with union when one branch is empty") {
    val scenario = ScenarioBuilder
      .streaming("sample-union")
      .sources(
        GraphBuilder
          .source("start-foo", TestScenarioRunner.testDataSource)
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", TestScenarioRunner.noopSource)
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union",
            Some(OutVariableName),
            List(
              BranchFooId -> List("Output expression" -> "{a: #input}".spel),
              BranchBarId -> List("Output expression" -> "{a: '123'}".spel)
            )
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> s"#$OutVariableName.a".spel)
      )

    val result = testScenarioRunner.runWithData(scenario, data)
    result.validValue shouldBe RunResult.successes(data)
  }

  test("should unify streams with union when both branches emit data") {
    val scenario = ScenarioBuilder
      .streaming("sample-union")
      .sources(
        GraphBuilder
          .source("start-foo", TestScenarioRunner.testDataSource)
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", TestScenarioRunner.testDataSource)
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union",
            Some(OutVariableName),
            List(
              BranchFooId -> List("Output expression" -> "{a: #input}".spel),
              BranchBarId -> List("Output expression" -> "{a: '123'}".spel)
            )
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> s"#$OutVariableName.a".spel)
      )

    val result = testScenarioRunner.runWithData(scenario, data).validValue
    result.successes.toSet shouldBe data.toSet + "123"
    result.errors shouldBe Nil
  }

  test("should throw when contexts are different") {
    val scenario = ScenarioBuilder
      .streaming("sample-union")
      .sources(
        GraphBuilder
          .source("start-foo", TestScenarioRunner.testDataSource)
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", "noopSource")
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union",
            Some(OutVariableName),
            List(
              BranchFooId -> List("Output expression" -> "{a: #input}".spel),
              BranchBarId -> List("Output expression" -> "{b: 123}".spel)
            )
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> s"#$OutVariableName.a".spel)
      )

    val result = testScenarioRunner.runWithData(scenario, data).invalidValue
    result.toList should contain(CannotCreateObjectError("All branch values must be of the same type", UnionNodeId))
  }

  test("should throw when one branch emits error") {
    val data = List(10, 20, 30, 40)

    val scenario = ScenarioBuilder
      .streaming("sample-union")
      .sources(
        GraphBuilder
          .source("start-foo", TestScenarioRunner.testDataSource)
          .branchEnd(BranchFooId, UnionNodeId),
        GraphBuilder
          .source("start-bar", TestScenarioRunner.testDataSource)
          .branchEnd(BranchBarId, UnionNodeId),
        GraphBuilder
          .join(
            UnionNodeId,
            "union",
            Some(OutVariableName),
            List(
              BranchFooId -> List("Output expression" -> "#input".spel),
              BranchBarId -> List("Output expression" -> "#input / (#input % 4)".spel)
            )
          )
          .emptySink("end", TestScenarioRunner.testResultSink, "value" -> s"#$OutVariableName".spel)
      )

    val result = testScenarioRunner.runWithData(scenario, data).validValue
    result.successes.size shouldBe 6
    result.successes.toSet shouldBe Set(5, 10, 15, 20, 30, 40)

    val errors = result.errors.map(_.throwable).map { exc =>
      exc.asInstanceOf[CustomNodeValidationException].getMessage
    }

    errors shouldBe List(
      "Expression [#input / (#input % 4)] evaluation failed, message: / by zero",
      "Expression [#input / (#input % 4)] evaluation failed, message: EL1072E: An exception occurred whilst evaluating a compiled expression"
    )
  }

  private def testScenarioRunner =
    TestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

}
