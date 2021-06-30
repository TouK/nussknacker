package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.{QueryArgument, QueryArguments, QueryArgumentsExtractor, SingleResultStrategy}
import pl.touk.nussknacker.sql.db.schema.TableDefinition
import pl.touk.nussknacker.sql.definition.TypeToParameterEditor
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.{CacheTTLParam, CacheTTLParamName, TransformationState}


object DatabaseLookupEnricher {

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
    val columnDef = tableDef.columnDefs.find(_.name == keyColumnName).getOrElse {
      // This error should only happen when defining a process via Nussknacker's programming interface.
      throw new IllegalArgumentException(s"Invalid key column: $keyColumnName. Available columns: ${tableDef.columnDefs.map(_.name).mkString(", ")}")
    }
    Parameter(KeyValueParamName, columnDef.typ)
      .copy(isLazyParameter = true, editor = TypeToParameterEditor(columnDef.typ))
  }
}

class DatabaseLookupEnricher(dBPoolConfig: DBPoolConfig) extends DatabaseQueryEnricher(dBPoolConfig) {
  import DatabaseLookupEnricher._

  override protected val queryArgumentsExtractor: QueryArgumentsExtractor =
    (argsCount: Int, params: Map[String, Any]) =>
      QueryArguments(
        QueryArgument(index = 1, value = params(KeyValueParamName)) :: Nil)

  override def initialParameters: List[Parameter] = TableParam :: CacheTTLParam :: Nil

  protected def tableParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                              (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep((TableParamName, DefinedEagerParameter(tableName: String, _)) :: (CacheTTLParamName, _) :: Nil, None) =>
      if (tableName.isEmpty) {
        FinalResults(context, errors = CustomNodeError("Table name is missing", Some(TableParamName)) :: Nil, state = None)
      } else {
        val query = s"SELECT * FROM $tableName"
        val queryMetaData = dbMetaDataProvider.getQueryMetaData(query)
        NextParameters(
          parameters = keyColumnParam(queryMetaData.tableDefinition) :: Nil,
          state = Some(TransformationState(query = query, argsCount = 1, queryMetaData.tableDefinition, strategy = SingleResultStrategy)))
      }
  }

  protected def keyColumnParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                  (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep((TableParamName, _) :: (CacheTTLParamName, _) :: (KeyColumnParamName, DefinedEagerParameter(keyColumn: String, _)) :: Nil, Some(state) ) =>
      val queryWithWhere = s"""${state.query} WHERE ${sqlDialect.quoteIdentifier(keyColumn)} = ?"""
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

}
