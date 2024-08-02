package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, invalid, valid}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Inside.inside
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  CustomNodeError,
  ExpressionParserCompilationError
}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.TestTableComponents._
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.PatientScalaFutures

class TableSinkParametersTest extends AnyFunSuite with FlinkSpec with Matchers with PatientScalaFutures {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val tableComponentsConfig: Config = ConfigFactory.parseString(s"""
       |{
       |  tableDefinitionFilePath: "engine/flink/components/base-tests/src/test/resources/tables-definition-table-sink-parameters-test.sql"
       |  enableFlinkBatchExecutionMode: true
       |}
       |""".stripMargin)

  private lazy val tableComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableComponentsConfig,
    ProcessObjectDependencies.withConfig(tableComponentsConfig)
  )

  private val inputTableName                 = "input"
  private val outputTableName                = "output"
  private val outputTableNameWithInvalidCols = "output_invalid_column_names"

  private lazy val runner: FlinkTestScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(singleRecordBatchTable :: tableComponents)
    .build()

  test("should take parameters per column in non-raw mode") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableName'".spel,
        "Raw editor" -> "false".spel,
        "client_id"  -> "''".spel,
        "amount"     -> "123.11".spel,
      )

    val result = runner.runWithoutData(scenario)
    result.isValid shouldBe true
  }

  test("should take parameters per column in non-raw mode - experiment with BigDecimal explicit") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableName'".spel,
        "Raw editor" -> "false".spel,
        "client_id"  -> "''".spel,
        "amount"     -> "T(java.math.BigDecimal).ONE".spel,
      )

    val result = runner.runWithoutData(scenario)
    result.isValid shouldBe true
  }

  test("should return errors for type errors in non-raw mode value parameters") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableName'".spel,
        "Raw editor" -> "false".spel,
        "client_id"  -> "123.11".spel,
        "amount"     -> "''".spel,
      )

    val result = runner.runWithoutData(scenario)
    inside(result) {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Bad expression type, expected: String, found: Double(123.11)",
                "end",
                _,
                _,
                _
              ),
              ExpressionParserCompilationError(
                "Bad expression type, expected: BigDecimal, found: String()",
                "end",
                _,
                _,
                _
              ) :: Nil
            )
          ) =>
    }
  }

  test("should return errors for illegally named columns for non-raw mode") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("start", "table", "Table" -> s"'$inputTableName'".spel)
      .emptySink(
        "end",
        "table",
        "Table"      -> s"'$outputTableNameWithInvalidCols'".spel,
        "Raw editor" -> "false".spel
      )

    val result = runner.runWithoutData(scenario)
    inside(result) { case Invalid(NonEmptyList(CustomNodeError("end", _, _), CustomNodeError("end", _, _) :: Nil)) =>
    }
  }

}
