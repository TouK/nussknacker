package pl.touk.nussknacker.engine.common.components

import cats.data.ValidatedNel
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.test.{RunListResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util.{List => JList, Map => JMap}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

trait DecisionTableSpec extends AnyFreeSpec with Matchers with ValidatedValuesDetailedMessage {

  import spel.Implicits._

  "Decision Table component should" - {
    "filter and return decision table's rows filtered by the expression" in {
      val scenarioWithDecisionTable = ScenarioBuilder
        .requestResponse("test scenario")
        .source("request", TestScenarioRunner.testDataSource)
        .enricher(
          "decision-table",
          "dtResult",
          "decision-table",
          "Basic Decision Table" -> decisionTableJson,
          "Expression"           -> "#ROW['B'] == 'foo' && #ROW['C'] != null",
        )
        .end("end", "value" -> "#dtResult")

      val validatedResult = runScenario[TestMessage, JList[JMap[String, Any]]](scenarioWithDecisionTable)(
        withData = List(TestMessage("1", 100))
      )

      val resultList = validatedResult.validValue.successes
      resultList should be(oneElementList {
        List(
          Map(
            "somename" -> 1.0,
            "B"        -> "foo",
            "C"        -> "bar"
          ).asJava
        ).asJava
      })
    }
  }

  protected def testScenarioRunner: TestScenarioRunner

  protected def runScenario[DATA: ClassTag, RESULT](scenario: CanonicalProcess)(
      withData: Iterable[DATA]
  ): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]]

  protected def addEndNode(
      builder: GraphBuilder[CanonicalProcess]
  )(id: String, params: Seq[(String, Expression)]): CanonicalProcess

  private lazy val decisionTableJson = Expression.tabularDataDefinition {
    s"""{
       |  "columns": [
       |    {
       |      "name": "somename",
       |      "type": "java.lang.Double"
       |    },
       |    {
       |      "name": "B",
       |      "type": "java.lang.String"
       |    },
       |    {
       |      "name": "C",
       |      "type": "java.lang.String"
       |    }
       |  ],
       |  "rows": [
       |    [
       |      null,
       |      null,
       |      "test"
       |    ],
       |    [
       |      "1",
       |      "foo",
       |      "bar"
       |    ],
       |    [
       |      null,
       |      null,
       |      "xxx"
       |    ]
       |  ]
       |}""".stripMargin
  }

  private def oneElementList[T](obj: T) = List(obj)

  private implicit class AddEndNodeExt(builder: GraphBuilder[CanonicalProcess]) {
    def end(id: String, params: (String, Expression)*): CanonicalProcess =
      addEndNode(builder)(id, params)
  }

}

private final case class TestMessage(id: String, value: Int)

class FlinkEngineRunDecisionTableSpec extends DecisionTableSpec with FlinkSpec {

  override protected lazy val testScenarioRunner: FlinkTestScenarioRunner =
    TestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .withExtraComponents(
        ComponentDefinition("decision-table", DecisionTable) :: Nil
      )
      .build()

  override protected def runScenario[DATA: ClassTag, RESULT](
      scenario: CanonicalProcess
  )(withData: Iterable[DATA]): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]] = {
    testScenarioRunner.runWithData(scenario, withData.toList)
  }

  override protected def addEndNode(
      builder: GraphBuilder[CanonicalProcess]
  )(id: String, params: Seq[(String, Expression)]): CanonicalProcess = {
    builder.processorEnd(id, TestScenarioRunner.testResultService, params: _*)
  }

}

class LiteEngineRunDecisionTableSpec extends DecisionTableSpec {

  override protected lazy val testScenarioRunner: LiteTestScenarioRunner =
    TestScenarioRunner
      .liteBased()
      .withExtraComponents(
        ComponentDefinition("decision-table", DecisionTable) :: Nil
      )
      .build()

  override protected def runScenario[DATA: ClassTag, RESULT](
      scenario: CanonicalProcess
  )(withData: Iterable[DATA]): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]] = {
    testScenarioRunner.runWithData(scenario, withData.toList)
  }

  override protected def addEndNode(
      builder: GraphBuilder[CanonicalProcess]
  )(id: String, params: Seq[(String, Expression)]): CanonicalProcess = {
    builder.emptySink(id, TestScenarioRunner.testResultSink, params: _*)
  }

}
