package pl.touk.nussknacker.engine.flink.table.sink

import org.apache.flink.table.api.DataTypes
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.DefinedEagerParameter
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.TableTestCases.SimpleTable
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkParametersTest.IllegalParamColumnNameCase
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.flink.table.{ColumnDefinition, TableDefinition, TableSqlDefinitions}

class TableSinkParametersTest extends AnyFunSuite with Matchers {

  private implicit val nodeId: NodeId = NodeId("id")

  private val tableSink = new TableSinkFactory(
    TableSqlDefinitions(
      List(SimpleTable.tableDefinition, IllegalParamColumnNameCase.tableDefinition),
      List(SimpleTable.sqlStatement, IllegalParamColumnNameCase.sqlStatement)
    )
  )

  test("should return parameters for columns for non-raw mode") {
    val ctxDefinition = tableSink.contextTransformation(ValidationContext(), List())
    val result = ctxDefinition(
      tableSink.TransformationStep(
        parameters = List(
          TableComponentFactory.tableNameParamName -> DefinedEagerParameter(
            SimpleTable.tableName,
            Typed[String]
          ),
          TableSinkFactory.rawModeParameterName -> DefinedEagerParameter(false, Typed[Boolean]),
        ),
        state = None
      )
    )
    inside(result) { case tableSink.NextParameters(params, _, _) =>
      params shouldBe List(
        Parameter(ParameterName("someString"), Typed[String]).copy(isLazyParameter = true),
        Parameter(ParameterName("someVarChar"), Typed[String]).copy(isLazyParameter = true),
        Parameter(ParameterName("someInt"), Typed[Integer]).copy(isLazyParameter = true),
      )
    }
  }

  test("should return errors for illegally named columns for non-raw mode") {
    val ctxDefinition = tableSink.contextTransformation(ValidationContext(), List())
    val result = ctxDefinition(
      tableSink.TransformationStep(
        parameters = List(
          TableComponentFactory.tableNameParamName -> DefinedEagerParameter(
            IllegalParamColumnNameCase.tableName,
            Typed[String]
          ),
          TableSinkFactory.rawModeParameterName -> DefinedEagerParameter(false, Typed[Boolean]),
        ),
        state = None
      )
    )
    inside(result) {
      case tableSink.NextParameters(params, CustomNodeError(_, _, _) :: CustomNodeError(_, _, _) :: Nil, _) =>
        params shouldBe Nil
    }
  }

}

object TableSinkParametersTest {

  object IllegalParamColumnNameCase {
    val tableName = "invalidParamsTable"

    val sqlStatement: String =
      s"""|CREATE TABLE $tableName
          |(
          |    `Raw editor`  STRING,
          |    `Table`       STRING
          |) WITH (
          |    'connector' = 'filesystem'
          |);""".stripMargin

    val schemaTypingResult: TypingResult = Typed.record(
      Map(
        "Raw editor" -> Typed[String],
        "Table"      -> Typed[String],
      )
    )

    val tableDefinition: TableDefinition = TableDefinition(
      tableName,
      schemaTypingResult,
      columns = List(
        ColumnDefinition("Raw editor", Typed[String], DataTypes.STRING()),
        ColumnDefinition("Table", Typed[String], DataTypes.STRING()),
      )
    )

  }

}
