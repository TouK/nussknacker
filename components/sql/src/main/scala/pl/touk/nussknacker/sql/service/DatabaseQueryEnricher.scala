package pl.touk.nussknacker.sql.service

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.sql.db.pool.{DBPoolConfig, HikariDataSourceFactory}
import pl.touk.nussknacker.sql.db.query._
import pl.touk.nussknacker.sql.db.schema.{DbMetaDataProvider, DbParameterMetaData, SqlDialect, TableDefinition}

import java.sql.{SQLException, SQLSyntaxErrorException}
import java.time.Duration
import java.time.temporal.ChronoUnit
import scala.util.control.NonFatal

object DatabaseQueryEnricher {
  final val ArgPrefix: String = "arg"

  final val CacheTTLParamName: String = "Cache TTL"

  final val QueryParamName: String = "Query"

  final val ResultStrategyParamName: String = "Result strategy"

  val metaData: TypedNodeDependency[MetaData] = TypedNodeDependency[MetaData]

  val CacheTTLParam: Parameter = Parameter.optional(CacheTTLParamName, Typed[Duration]).copy(
    editor = Some(DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)))
  )

  val QueryParam: Parameter = Parameter(QueryParamName, Typed[String]).copy(
    editor = Some(SqlParameterEditor))

  val ResultStrategyParam: Parameter = {
    val strategyNames = List(SingleResultStrategy.name, ResultSetStrategy.name).map { strategyName =>
      FixedExpressionValue(s"'$strategyName'", strategyName)
    }
    Parameter(ResultStrategyParamName, Typed[String]).copy(
      editor = Some(FixedValuesParameterEditor(strategyNames))
    )
  }

  case class TransformationState(query: String,
                                 argsCount: Int,
                                 tableDef: TableDefinition,
                                 strategy: QueryResultStrategy
                                ) {
    val outputType: TypingResult = strategy.resultType(tableDef)
  }
}

/*
TODO:
1. Named parameters. Maybe we can make use of Spring's NamedJdbcParameterTemplate?
2. Typed parameters - currently we type them as Objects/Unknowns
*/
class DatabaseQueryEnricher(val dbPoolConfig: DBPoolConfig, val dbMetaDataProvider: DbMetaDataProvider) extends EagerService
  with SingleInputGenericNodeTransformation[ServiceInvoker] with LazyLogging {

  import DatabaseQueryEnricher._

  override type State = TransformationState
  protected lazy val sqlDialect = new SqlDialect(dbMetaDataProvider.getDialectMetaData)
  override val nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: metaData :: Nil
  protected val queryArgumentsExtractor: (Int, Map[String, Any]) => QueryArguments = (argsCount: Int, params: Map[String, Any]) => {
    QueryArguments(
      (1 to argsCount).map { argNo =>
        val paramName = s"$ArgPrefix$argNo"
        QueryArgument(index = argNo, value = params(paramName))
      }.toList
    )
  }
  protected var dataSource: HikariDataSource = _

  override def open(engineRuntimeContext: EngineRuntimeContext): Unit = {
    try
      dataSource = HikariDataSourceFactory(dbPoolConfig)
    finally
      super.open(engineRuntimeContext)
  }

  override def close(): Unit = {
    try
      dataSource.close()
    finally
      super.close()
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition =
    initialStep(context, dependencies) orElse
      queryParamStep(context, dependencies) orElse
      finalStep(context, dependencies)

  protected def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                           (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(parameters = ResultStrategyParam :: QueryParam :: CacheTTLParam :: Nil)
  }

  protected def queryParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                              (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep((ResultStrategyParamName, DefinedEagerParameter(strategyName: String, _)) :: (QueryParamName, DefinedEagerParameter(query: String, _)) :: (CacheTTLParamName, _) :: Nil, None) =>
      if (query.isEmpty) {
        FinalResults(
          context, errors = CustomNodeError("Query is missing", Some(QueryParamName)) :: Nil, state = None)
      } else {
        parseQuery(context, dependencies, strategyName, query)
      }
  }

  private def parseQuery(context: ValidationContext, dependencies: List[NodeDependencyValue],
                         strategyName: String, query: String)(implicit nodeId: NodeId): TransformationStepResult = {
    try {
      val queryMetaData = dbMetaDataProvider.getQueryMetaData(query)
      val queryArgParams = toParameters(queryMetaData.dbParameterMetaData)
      NextParameters(
        parameters = queryArgParams,
        state = Some(TransformationState(
          query = query,
          argsCount = queryArgParams.size,
          tableDef = queryMetaData.tableDefinition,
          strategy = QueryResultStrategy(strategyName).get)
        )
      )
    } catch {
      case e: SQLException =>
        val error = CustomNodeError(messageFromSQLException(query, e), Some(DatabaseQueryEnricher.QueryParamName))
        FinalResults.forValidation(context, errors = List(error))(_.withVariable(
          name = OutputVariableNameDependency.extract(dependencies),
          value = Unknown,
          paramName = None)
        )
    }
  }

  //SyntaxErrorException should have message meaningful for users, for others (connection problem?) it's better
  //to return generic message and log details...
  private def messageFromSQLException(query: String, sqlException: SQLException): String = sqlException match {
    case e: SQLSyntaxErrorException =>
      e.getMessage
    case e =>
      logger.info(s"Failed to execute query: $query", e)
      s"Failed to execute query: ${e.getClass.getSimpleName}"
  }

  protected def toParameters(dbParameterMetaData: DbParameterMetaData): List[Parameter] =
    (1 to dbParameterMetaData.parameterCount).map { paramNo =>
      Parameter(s"$ArgPrefix$paramNo", typing.Unknown).copy(isLazyParameter = true)
    }.toList

  protected def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])
                         (implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(_, Some(state)) =>
      FinalResults.forValidation(context, state = Some(state))(_.withVariable(
        name = OutputVariableNameDependency.extract(dependencies),
        value = state.outputType,
        paramName = None))
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[TransformationState]): ServiceInvoker = {
    val state = finalState.get
    val cacheTTLOption = extractOptional[Duration](params, CacheTTLParamName)
    cacheTTLOption match {
      case Some(cacheTTL) =>
        new DatabaseEnricherInvokerWithCache(state.query, state.argsCount, state.tableDef, state.strategy,
          queryArgumentsExtractor, cacheTTL, state.outputType, () => dataSource.getConnection())
      case None =>
        new DatabaseEnricherInvoker(state.query, state.argsCount, state.tableDef, state.strategy,
          queryArgumentsExtractor, state.outputType, () => dataSource.getConnection())
    }
  }

  protected def extractOptional[T](params: Map[String, Any], paramName: String): Option[T] =
    params.get(paramName).flatMap(Option(_)).map(_.asInstanceOf[T])


}
