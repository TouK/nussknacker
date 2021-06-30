package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{ContextId, ServiceInvoker}
import pl.touk.nussknacker.sql.db.WithDBConnectionPool
import pl.touk.nussknacker.sql.db.query.{QueryArguments, QueryArgumentsExtractor, QueryExecutor, QueryResultStrategy, ResultSetQueryExecutor, ResultSetStrategy, SingleResultQueryExecutor, SingleResultStrategy}
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.{Connection, PreparedStatement}
import scala.concurrent.{ExecutionContext, Future}

// TODO: cache prepared statement?
class DatabaseEnricherInvoker(query: String,
                              argsCount: Int,
                              tableDef: TableDefinition,
                              strategy: QueryResultStrategy,
                              queryArgumentsExtractor: QueryArgumentsExtractor,
                              val returnType: typing.TypingResult,
                              val getConnection: () => Connection) extends ServiceInvoker with WithDBConnectionPool {

  protected val queryExecutor: QueryExecutor = strategy match {
    case SingleResultStrategy => new SingleResultQueryExecutor(tableDef)
    case ResultSetStrategy => new ResultSetQueryExecutor(tableDef)
  }

  override def invokeService(params: Map[String, Any])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId): Future[queryExecutor.QueryResult] =
    Future.successful {
      queryDatabase(queryArgumentsExtractor(argsCount, params))
    }

  protected def queryDatabase(queryArguments: QueryArguments): queryExecutor.QueryResult =
    withConnection(query) { statement =>
      setQueryArguments(statement, queryArguments)
      queryExecutor.execute(statement)
    }

  protected def setQueryArguments(statement: PreparedStatement, queryArguments: QueryArguments): Unit =
    queryArguments.value.foreach { arg =>
      statement.setObject(arg.index, arg.value)
    }
}