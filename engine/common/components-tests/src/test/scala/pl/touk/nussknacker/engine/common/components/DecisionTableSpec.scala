package pl.touk.nussknacker.engine.common.components

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import scala.jdk.CollectionConverters._
import java.util.{List => JList}
import java.util.{Map => JMap}

class DecisionTableSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  import spel.Implicits._

  private val testScenarioRunner = TestScenarioRunner
    .liteBased()
    .build()

  test("simple test") { // todo: change name
    val scenarioWithDecisionTable = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "decision-table",
        "dtResult",
        "decision-table",
        "Basic Decision Table" -> decisionTableJson,
        "Expression"           -> "#DecisionTableRow['B'] == 'foo' && #DecisionTableRow['C'] != null",
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#dtResult")

    val validatedResult = testScenarioRunner.runWithData[TestMessage, JList[JMap[String, Any]]](
      scenario = scenarioWithDecisionTable,
      data = List(TestMessage("1", 100))
    )

    val resultList = validatedResult.validValue.successes
    resultList should be(oneElementList {
      List(
        Map(
          "somename" -> 1,
          "B"        -> "foo",
          "C"        -> "bar"
        ).asJava
      ).asJava
    })
  }

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
       |      1,
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
}

private final case class TestMessage(id: String, value: Int)
