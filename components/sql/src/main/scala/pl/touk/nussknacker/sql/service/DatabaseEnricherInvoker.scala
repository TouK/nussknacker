package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.{Context, ServiceInvoker}
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.sql.db.WithDBConnectionPool
import pl.touk.nussknacker.sql.db.query._
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.{Connection, PreparedStatement}
import scala.concurrent.{ExecutionContext, Future}

// TODO: cache prepared statement?
class DatabaseEnricherInvoker(
    query: String,
    argsCount: Int,
    tableDef: TableDefinition,
    strategy: QueryResultStrategy,
    queryArgumentsExtractor: (Int, Map[String, Any]) => QueryArguments,
    val returnType: typing.TypingResult,
    val getConnection: () => Connection,
    val getTimeMeasurement: () => AsyncExecutionTimeMeasurement,
    params: Map[String, Any],
) extends ServiceInvoker
    with WithDBConnectionPool {

  protected val queryExecutor: QueryExecutor = strategy match {
    case SingleResultStrategy => new SingleResultQueryExecutor(tableDef)
    case ResultSetStrategy    => new ResultSetQueryExecutor(tableDef)
    case UpdateResultStrategy => new UpdateQueryExecutor()
  }

  override def invokeService(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase
  ): Future[queryExecutor.QueryResult] = {
    getTimeMeasurement().measuring {
      queryDatabase(queryArgumentsExtractor(argsCount, params))
    }
  }

  protected def queryDatabase(
      queryArguments: QueryArguments
  )(implicit ec: ExecutionContext): Future[queryExecutor.QueryResult] =
    Future {
      withConnection(query) { statement =>
        setQueryArguments(statement, queryArguments)
        queryExecutor.execute(statement)
      }
    }

  protected def setQueryArguments(statement: PreparedStatement, queryArguments: QueryArguments): Unit =
    queryArguments.value.foreach { arg =>
      statement.setObject(arg.index, arg.value)
    }

}
