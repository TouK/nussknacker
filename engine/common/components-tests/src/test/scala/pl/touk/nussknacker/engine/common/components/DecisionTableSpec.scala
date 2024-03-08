package pl.touk.nussknacker.engine.common.components

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.scalatest.Inside
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.TabularDataDefinitionParserErrorDetails
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.TabularDataDefinitionParserErrorDetails.CellError
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

import java.time.LocalDate
import java.util.{List => JList, Map => JMap}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

trait DecisionTableSpec extends AnyFreeSpec with Matchers with ValidatedValuesDetailedMessage with Inside {

  import spel.Implicits._

  "Decision Table component should" - {
    "filter and return decision table's rows filtered by the expression" in {
      val result = execute[TestMessage, SCENARIO_RESULT](
        scenario = decisionTableExampleScenario(
          expression = "#ROW['age'] > #input.minAge && #ROW['DoB'] != null"
        ),
        withData = List(
          TestMessage(id = "1", minAge = 30),
          TestMessage(id = "2", minAge = 18)
        )
      )

      inside(result) { case Validated.Valid(r) =>
        r.errors should be(List.empty)
        r.successes should be(
          List(
            rows(
              rowData(name = "Mark", age = 54, dob = LocalDate.parse("1970-12-30"))
            ),
            rows(
              rowData(name = "Lisa", age = 21, dob = LocalDate.parse("2003-01-13")),
              rowData(name = "Mark", age = 54, dob = LocalDate.parse("1970-12-30"))
            )
          )
        )
      }
    }

    "return proper typingResult as java list which allows to run method on" in {
      val result = execute[TestMessage, SCENARIO_RESULT](
        scenario = decisionTableExampleScenario(
          expression = "#ROW['age'] > #input.minAge && #ROW['DoB'] != null",
          sinkValueExpression = "#dtResult.size"
        ),
        withData = List(
          TestMessage(id = "1", minAge = 30),
          TestMessage(id = "2", minAge = 18)
        )
      )

      inside(result) { case Validated.Valid(r) =>
        r.errors should be(List.empty)
        r.successes should be(List(1, 2))
      }
    }

    "fail to compile expression when" - {
      "non-present column name is used" in {
        val result = execute[TestMessage, SCENARIO_RESULT](
          scenario = decisionTableExampleScenario(
            expression = "#ROW['years'] > #input.minAge"
          ),
          withData = List(
            TestMessage(id = "1", minAge = 30),
            TestMessage(id = "2", minAge = 18)
          )
        )
        inside(result) { case Validated.Invalid(errors) =>
          errors should be(
            NonEmptyList.one(
              ExpressionParserCompilationError(
                message = "There is no property 'years' in type: Record{DoB: LocalDate, age: Integer, name: String}",
                nodeId = "decision-table",
                fieldName = Some("Expression"),
                originalExpr = "#ROW['years'] > #input.minAge",
                details = None
              )
            )
          )
        }
      }
      "type of the accessed column is wrong" in {
        val result = execute[TestMessage, SCENARIO_RESULT](
          scenario = decisionTableExampleScenario(
            expression = "#ROW['name'] > #input.minAge"
          ),
          withData = List(
            TestMessage(id = "1", minAge = 30),
            TestMessage(id = "2", minAge = 18)
          )
        )
        inside(result) { case Validated.Invalid(errors) =>
          errors should be(
            NonEmptyList.one(
              ExpressionParserCompilationError(
                message = "Wrong part types",
                nodeId = "decision-table",
                fieldName = Some("Expression"),
                originalExpr = "#ROW['name'] > #input.minAge",
                details = None
              )
            )
          )
        }
      }
    }
    "fail to compile tabular data definition when" - {
      "not supported type of column is used" in {
        val result = execute[TestMessage, SCENARIO_RESULT](
          scenario = decisionTableExampleScenario(
            basicDecisionTableDefinition = invalidColumnTypeDecisionTableJson,
            expression = "#ROW['age'] > #input.minAge",
          ),
          withData = List(
            TestMessage(id = "1", minAge = 30),
            TestMessage(id = "2", minAge = 18)
          )
        )
        inside(result) { case Validated.Invalid(errors) =>
          errors should be(
            NonEmptyList.one(
              ExpressionParserCompilationError(
                message = "Typing error in some cells",
                nodeId = "decision-table",
                fieldName = Some("Basic Decision Table"),
                originalExpr = invalidColumnTypeDecisionTableJson.expression,
                details = Some(
                  TabularDataDefinitionParserErrorDetails(
                    List(
                      CellError(
                        columnName = "name",
                        rowIndex = 0,
                        errorMessage = "Column has 'Object' type but its value cannot be converted to the type."
                      ),
                      CellError(
                        columnName = "name",
                        rowIndex = 1,
                        errorMessage = "Column has 'Object' type but its value cannot be converted to the type."
                      ),
                      CellError(
                        columnName = "name",
                        rowIndex = 2,
                        errorMessage = "Column has 'Object' type but its value cannot be converted to the type."
                      )
                    )
                  )
                )
              )
            )
          )
        }
      }
    }
  }

  private type SCENARIO_RESULT = JList[JMap[String, Any]]

  private lazy val exampleDecisionTableJson = Expression.tabularDataDefinition {
    s"""{
       |  "columns": [
       |    { "name": "name", "type": "java.lang.String" },
       |    { "name": "age", "type": "java.lang.Integer" },
       |    { "name": "DoB", "type": "java.time.LocalDate" }
       |  ],
       |  "rows": [
       |    [ "John", "39", null ],
       |    [ "Lisa", "21", "2003-01-13" ],
       |    [ "Mark", "54", "1970-12-30" ]
       |  ]
       |}""".stripMargin
  }

  private lazy val invalidColumnTypeDecisionTableJson = Expression.tabularDataDefinition {
    s"""{
       |  "columns": [
       |    { "name": "name", "type": "java.lang.Object" },
       |    { "name": "age", "type": "java.lang.Integer" },
       |    { "name": "DoB", "type": "java.time.LocalDate" }
       |  ],
       |  "rows": [
       |    [ "John", "39", null ],
       |    [ "Lisa", "21", "2003-01-13" ],
       |    [ "Mark", "54", "1970-12-30" ]
       |  ]
       |}""".stripMargin
  }

  private def decisionTableExampleScenario(
      expression: Expression,
      sinkValueExpression: Expression = "#dtResult",
      basicDecisionTableDefinition: Expression = exampleDecisionTableJson
  ) = {
    ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "decision-table",
        "dtResult",
        "decision-table",
        "Basic Decision Table" -> basicDecisionTableDefinition,
        "Expression"           -> expression,
      )
      .end("end", "value" -> sinkValueExpression)
  }

  private def rows(maps: java.util.Map[String, Any]*) = List(maps: _*).asJava

  private def rowData(name: String, age: Int, dob: LocalDate) =
    Map("name" -> name, "age" -> age, "DoB" -> dob).asJava

  protected def testScenarioRunner: TestScenarioRunner

  protected def execute[DATA: ClassTag, RESULT](
      scenario: CanonicalProcess,
      withData: Iterable[DATA]
  ): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]]

  protected def addEndNode(
      builder: GraphBuilder[CanonicalProcess]
  )(id: String, params: Seq[(String, Expression)]): CanonicalProcess

  private implicit class AddEndNodeExt(builder: GraphBuilder[CanonicalProcess]) {
    def end(id: String, params: (String, Expression)*): CanonicalProcess =
      addEndNode(builder)(id, params)
  }

}

private final case class TestMessage(id: String, minAge: Int)

class FlinkEngineRunDecisionTableSpec extends DecisionTableSpec with FlinkSpec {

  override protected lazy val testScenarioRunner: FlinkTestScenarioRunner =
    TestScenarioRunner
      .flinkBased(config, flinkMiniCluster)
      .build()

  override protected def execute[DATA: ClassTag, RESULT](
      scenario: CanonicalProcess,
      withData: Iterable[DATA]
  ): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]] = {
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
      .build()

  override protected def execute[DATA: ClassTag, RESULT](
      scenario: CanonicalProcess,
      withData: Iterable[DATA]
  ): ValidatedNel[ProcessCompilationError, RunListResult[RESULT]] = {
    testScenarioRunner.runWithData(scenario, withData.toList)
  }

  override protected def addEndNode(
      builder: GraphBuilder[CanonicalProcess]
  )(id: String, params: Seq[(String, Expression)]): CanonicalProcess = {
    builder.emptySink(id, TestScenarioRunner.testResultService, params: _*)
  }

}
