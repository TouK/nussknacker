package pl.touk.nussknacker.sql.service

import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.HikariDataSource
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService
import pl.touk.nussknacker.sql.db.pool.{DBPoolConfig, HikariDataSourceFactory}
import pl.touk.nussknacker.sql.db.query._
import pl.touk.nussknacker.sql.db.schema.{DbMetaDataProvider, DbParameterMetaData, SqlDialect, TableDefinition}

import java.sql.{SQLException, SQLSyntaxErrorException}
import java.time.Duration
import java.time.temporal.ChronoUnit

object DatabaseQueryEnricher {
  final val ArgPrefix: String = "arg"

  final val CacheTTLParamName: String = "Cache TTL"

  final val QueryParamName: String = "Query"

  final val ResultStrategyParamName: String = "Result strategy"

  val metaData: TypedNodeDependency[MetaData] = TypedNodeDependency[MetaData]

  val CacheTTLParam: Parameter = Parameter
    .optional(CacheTTLParamName, Typed[Duration])
    .copy(
      editor = Some(DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)))
    )

  val QueryParam: Parameter = Parameter(QueryParamName, Typed[String]).copy(editor = Some(SqlParameterEditor))

  val ResultStrategyParam: Parameter = {
    val strategyNames = List(SingleResultStrategy.name, ResultSetStrategy.name, UpdateResultStrategy.name).map {
      strategyName => FixedExpressionValue(s"'$strategyName'", strategyName)
    }
    Parameter(ResultStrategyParamName, Typed[String]).copy(
      editor = Some(FixedValuesParameterEditor(strategyNames))
    )
  }

  final case class TransformationState(
      query: String,
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
class DatabaseQueryEnricher(val dbPoolConfig: DBPoolConfig, val dbMetaDataProvider: DbMetaDataProvider)
    extends EagerService
    with TimeMeasuringService
    with SingleInputDynamicComponent[ServiceInvoker]
    with LazyLogging {

  import DatabaseQueryEnricher._

  override protected def serviceName: String = "dbQueryEnricher"

  override type State = TransformationState
  protected lazy val sqlDialect                       = new SqlDialect(dbMetaDataProvider.getDialectMetaData)
  override val nodeDependencies: List[NodeDependency] = OutputVariableNameDependency :: metaData :: Nil

  protected val queryArgumentsExtractor: (Int, Params, Context) => QueryArguments =
    (argsCount: Int, params: Params, context: Context) => {
      QueryArguments(
        (1 to argsCount).map { argNo =>
          QueryArgument(index = argNo, value = params.extractOrEvaluateUnsafe(s"$ArgPrefix$argNo", context))
        }.toList
      )
    }

  @transient
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

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    initialStep(context, dependencies) orElse
      queryParamStep(context, dependencies) orElse
      finalStep(context, dependencies)

  protected def initialStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(parameters = ResultStrategyParam :: QueryParam :: CacheTTLParam :: Nil)
  }

  protected def queryParamStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (ResultStrategyParamName, DefinedEagerParameter(strategyName: String, _)) :: (
            QueryParamName,
            DefinedEagerParameter(query: String, _)
          ) :: (CacheTTLParamName, _) :: Nil,
          None
        ) =>
      if (query.isEmpty) {
        FinalResults(context, errors = CustomNodeError("Query is missing", Some(QueryParamName)) :: Nil, state = None)
      } else {
        parseQuery(context, dependencies, strategyName, query)
      }
  }

  private def tableDefinitionForStrategyOrError(
      tableDefinition: Option[TableDefinition],
      strategy: QueryResultStrategy
  ): Either[String, TableDefinition] = {
    (strategy, tableDefinition) match {
      case (UpdateResultStrategy, None) => Right(TableDefinition(Nil))
      case (_, Some(tableDefinition))   => Right(tableDefinition)
      case (_, None)                    => Left("Prepared query returns no columns")
    }
  }

  private def parseQuery(
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      strategyName: String,
      query: String
  )(implicit nodeId: NodeId): TransformationStepResult = {
    try {
      val queryMetaData  = dbMetaDataProvider.getQueryMetaData(query)
      val queryArgParams = toParameters(queryMetaData.dbParameterMetaData)
      val strategy       = QueryResultStrategy(strategyName).get
      tableDefinitionForStrategyOrError(queryMetaData.tableDefinition, strategy) match {
        case Left(errorMsg) =>
          FinalResults(
            context,
            errors = CustomNodeError(errorMsg, Some(QueryParamName)) :: Nil,
            state = None
          )
        case Right(tableDefinition) =>
          val state = TransformationState(
            query = query,
            argsCount = queryArgParams.size,
            tableDef = tableDefinition,
            strategy = strategy
          )
          if (queryArgParams.isEmpty) {
            createFinalResults(context, dependencies, state)
          } else {
            NextParameters(parameters = queryArgParams, state = Some(state))
          }
      }
    } catch {
      case e: SQLException =>
        val error = CustomNodeError(messageFromSQLException(query, e), Some(QueryParamName))
        FinalResults.forValidation(context, errors = List(error))(
          _.withVariable(name = OutputVariableNameDependency.extract(dependencies), value = Unknown, paramName = None)
        )
    }
  }

  private def messageFromSQLException(query: String, sqlException: SQLException): String = sqlException match {
    case e: SQLSyntaxErrorException => e.getMessage
    case e                          =>
      // We choose to inform user about all problems, such as missing column as well as auth / connection issues
      logger.warn(s"Failed to execute query: $query", e)
      s"Failed to execute query: $e"
  }

  protected def toParameters(dbParameterMetaData: DbParameterMetaData): List[Parameter] =
    (1 to dbParameterMetaData.parameterCount).map { paramNo =>
      Parameter(s"$ArgPrefix$paramNo", typing.Unknown).copy(isLazyParameter = true)
    }.toList

  protected def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(_, Some(state)) =>
    createFinalResults(context, dependencies, state)
  }

  private def createFinalResults(
      context: ValidationContext,
      dependencies: List[NodeDependencyValue],
      state: TransformationState
  )(implicit nodeId: NodeId): FinalResults = {
    FinalResults.forValidation(context, state = Some(state))(
      _.withVariable(
        name = OutputVariableNameDependency.extract(dependencies),
        value = state.outputType,
        paramName = None
      )
    )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[TransformationState]
  ): ServiceInvoker = {
    val state          = finalState.get
    val cacheTTLOption = params.extract[Duration](CacheTTLParamName)
    cacheTTLOption match {
      case Some(cacheTTL) =>
        new DatabaseEnricherInvokerWithCache(
          state.query,
          state.argsCount,
          state.tableDef,
          state.strategy,
          queryArgumentsExtractor,
          cacheTTL,
          state.outputType,
          () => dataSource.getConnection(),
          () => timeMeasurement,
          params
        )
      case None =>
        new DatabaseEnricherInvoker(
          state.query,
          state.argsCount,
          state.tableDef,
          state.strategy,
          queryArgumentsExtractor,
          state.outputType,
          () => dataSource.getConnection(),
          () => timeMeasurement,
          params
        )
    }
  }

}
