package pl.touk.nussknacker.sql.service

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{Context, NodeId, Params}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.db.query.{QueryArgument, QueryArguments, SingleResultStrategy}
import pl.touk.nussknacker.sql.db.schema.{DbMetaDataProvider, SchemaDefinition, TableDefinition}
import pl.touk.nussknacker.sql.service.DatabaseLookupEnricher.TableParamName
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher.{
  cacheTTLParamDeclaration,
  cacheTTLParamName,
  TransformationState
}

import scala.util.control.NonFatal

object DatabaseLookupEnricher {

  final val TableParamName: ParameterName = ParameterName("Table")

  final val KeyColumnParamName: ParameterName = ParameterName("Key column")

  final val KeyValueParamName: ParameterName = ParameterName("Key value")

  private def keyColumnParam(tableDef: TableDefinition): Parameter = {
    val columnNameValues = tableDef.columnDefs.map(column => FixedExpressionValue(s"'${column.name}'", column.name))
    Parameter(KeyColumnParamName, Typed[String])
      .copy(editor = Some(FixedValuesParameterEditor(columnNameValues)))
  }

  private def keyValueParam(keyColumnName: String, tableDef: TableDefinition): Parameter = {
    val columnDef = tableDef.columnDefs.find(_.name == keyColumnName).getOrElse {
      // This error should only happen when defining a process via Nussknacker's programming interface.
      throw new IllegalArgumentException(
        s"Invalid key column: $keyColumnName. Available columns: ${tableDef.columnDefs.map(_.name).mkString(", ")}"
      )
    }
    Parameter(KeyValueParamName, columnDef.typing).copy(isLazyParameter = true)
  }

}

class DatabaseLookupEnricher(dBPoolConfig: DBPoolConfig, dbMetaDataProvider: DbMetaDataProvider)
    extends DatabaseQueryEnricher(dBPoolConfig, dbMetaDataProvider)
    with LazyLogging {

  private def tableParam(): Parameter = {
    val schemaMetaData =
      try {
        dbMetaDataProvider.getSchemaDefinition()
      } catch {
        case NonFatal(e) =>
          logger.warn(s"Cannot fetch schema metadata for ${dBPoolConfig.url}", e)
          SchemaDefinition.empty()
      }

    val possibleTables: List[FixedExpressionValue] =
      schemaMetaData.tables.map(table => FixedExpressionValue(s"'$table'", table))
    Parameter(TableParamName, Typed[String]).copy(editor = Some(FixedValuesParameterEditor(possibleTables)))
  }

  import DatabaseLookupEnricher._

  override protected val queryArgumentsExtractor: (Int, Params, Context) => QueryArguments =
    (_: Int, params: Params, context: Context) => {
      QueryArguments(
        QueryArgument(
          index = 1,
          value = params.extractOrEvaluateLazyParam(KeyValueParamName, context)
        ) :: Nil
      )
    }

  override protected def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(parameters = tableParam() :: cacheTTLParamDeclaration.createParameter() :: Nil)
  }

  private def tableParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (TableParamName, DefinedEagerParameter(tableName: String, _)) :: (`cacheTTLParamName`, _) :: Nil,
          None
        ) =>
      if (tableName.isEmpty) {
        FinalResults(
          context,
          errors = CustomNodeError("Table name is missing", Some(TableParamName)) :: Nil,
          state = None
        )
      } else {
        val query = s"SELECT * FROM $tableName"
        dbMetaDataProvider.getTableMetaData(tableName).tableDefinition match {
          case Some(tableDefinition) =>
            NextParameters(
              parameters = keyColumnParam(tableDefinition) :: Nil,
              state = Some(
                TransformationState(
                  query = query,
                  argsCount = 1,
                  tableDefinition,
                  strategy = SingleResultStrategy
                )
              )
            )
          case None =>
            FinalResults(
              context,
              errors = CustomNodeError("Prepared query returns no columns", Some(TableParamName)) :: Nil,
              state = None
            )
        }
      }
  }

  private def keyColumnParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (TableParamName, _) :: (`cacheTTLParamName`, _) :: (
            KeyColumnParamName,
            DefinedEagerParameter(keyColumn: String, _)
          ) :: Nil,
          Some(state)
        ) =>
      val queryWithWhere = s"""${state.query} WHERE ${sqlDialect.quoteIdentifier(keyColumn)} = ?"""
      val newState       = state.copy(query = queryWithWhere)
      NextParameters(
        parameters = keyValueParam(keyColumn, state.tableDef) :: Nil,
        state = Some(newState)
      )
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    initialStep(context, dependencies) orElse
      tableParamStep(context, dependencies) orElse
      keyColumnParamStep(context, dependencies) orElse
      finalStep(context, dependencies)

}
