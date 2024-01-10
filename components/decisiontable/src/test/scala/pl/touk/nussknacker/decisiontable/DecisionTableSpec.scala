package pl.touk.nussknacker.decisiontable

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.{Cell, Row}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class DecisionTableSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  import spel.Implicits._

  private val testScenarioRunner = TestScenarioRunner
    .liteBased()
    .withExtraComponents {
      new DecisionTableComponentProvider()
        .create(
          ConfigFactory.empty(),
          ProcessObjectDependencies.empty
        )
    }
    .build()

  test("simple test") { // todo: change name
    val process = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "decision-table",
        "dtResult",
        "decision-table",
        "Basic Decision Table" -> decisionTableJson,
        "Expression"           -> "#DecisionTable.B == 'foo'",
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#dtResult")

    val validatedResult = testScenarioRunner.runWithData[TestMessage, Vector[Row]](process, List(TestMessage("1", 100)))

    val resultList = validatedResult.validValue.successes
    resultList should be(
      List {
        Vector(
          Row(
            Vector(
              Cell(classOf[Option[Int]], Some(1)),
              Cell(classOf[String], "foo"),
              Cell(classOf[String], "bar"),
            )
          )
        )
      }
    )
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

}

private final case class TestMessage(id: String, value: Int)
