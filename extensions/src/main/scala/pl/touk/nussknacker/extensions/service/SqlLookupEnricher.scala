package pl.touk.nussknacker.extensions.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, OutputVariableNameDependency, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.extensions.db.query.SingleResultStrategy
import pl.touk.nussknacker.extensions.db.schema.TableDefinition
import pl.touk.nussknacker.extensions.definition.TypeToParameterEditor
import pl.touk.nussknacker.extensions.service.SqlEnricher.TransformationState

import java.sql.PreparedStatement
import javax.sql.DataSource

object SqlLookupEnricher {

  final val TableParamName: String = "Table"

  final val KeyColumnParamName: String = "Key column"

  final val KeyValueParamName: String = "Key value"

  final val TableParam: Parameter = Parameter(TableParamName, Typed[String])
    .copy(editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW)))

  private def keyColumnParam(tableDef: TableDefinition): Parameter = {
    val columnNameValues = tableDef.columnDefs.map(column => FixedExpressionValue(s"'${column.name}'", column.name))
    Parameter(KeyColumnParamName, Typed[String])
      .copy(editor = Some(FixedValuesParameterEditor(columnNameValues)))
  }

  private def keyValueParam(keyColumnName: String, tableDef: TableDefinition): Parameter = {
    val columnDef = tableDef.columnDefs.find(_.name == keyColumnName).get
    Parameter(KeyValueParamName, columnDef.typ)
      .copy(isLazyParameter = true, editor = TypeToParameterEditor(columnDef.typ))
  }
}

class SqlLookupEnricher(override val dataSource: DataSource) extends SqlEnricher(dataSource) {
  import SqlLookupEnricher._

  override def initialParameters: List[Parameter] = TableParam :: Nil

  protected def tableParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                              (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep((TableParamName, DefinedEagerParameter(tableName: String, _)) :: Nil, None) =>
      if (tableName.isBlank)
        FinalResults(context, errors = CustomNodeError("Table name is missing", Some(TableParamName)) :: Nil, state = None)
      else {
        val query = s"SELECT * FROM $tableName"
        withConnection(query) { statement =>
          val tableDef = TableDefinition(statement.getMetaData)
          NextParameters(
            parameters = keyColumnParam(tableDef) :: Nil,
            state = Some(TransformationState(query = query, argsCount = 1, tableDef, strategy = SingleResultStrategy))
          )
        }
      }
  }

  protected def keyColumnParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                  (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep((TableParamName, _) :: (KeyColumnParamName, DefinedEagerParameter(keyColumn: String, _)) :: Nil, Some(state) ) =>
      val queryWithWhere = s"${state.query} WHERE $keyColumn = ?"
      val newState = state.copy(query = queryWithWhere)
      NextParameters(
        parameters = keyValueParam(keyColumn, state.tableDef) :: Nil,
        state = Some(newState)
      )
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition =
    initialStep(context, dependencies) orElse
      tableParamStep(context, dependencies) orElse
        keyColumnParamStep(context, dependencies) orElse
          finalStep(context, dependencies)

  override protected def setQueryArguments(statement: PreparedStatement, argsCount: Int, serviceInvokeParams: Map[String, Any]): Unit = {
    val keyValueQueryArgName = s"${SqlEnricher.ArgPrefix}1"
    val keyValue = serviceInvokeParams(KeyValueParamName)
    super.setQueryArguments(statement, argsCount, Map(keyValueQueryArgName -> keyValue))
  }
}
