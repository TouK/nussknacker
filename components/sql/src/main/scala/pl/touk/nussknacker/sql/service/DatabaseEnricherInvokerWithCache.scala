package pl.touk.nussknacker.sql.service

import com.github.benmanes.caffeine.cache.{AsyncCache, Caffeine}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.sql.db.query.{QueryArguments, QueryResultStrategy}
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.Connection
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

object DatabaseEnricherInvokerWithCache {

  final case class CacheKey(query: String, queryArguments: QueryArguments)
  final case class CacheEntry[+A](value: A)
}

class DatabaseEnricherInvokerWithCache(
    query: String,
    argsCount: Int,
    tableDef: TableDefinition,
    strategy: QueryResultStrategy,
    queryArgumentsExtractor: (Int, Params, Context) => QueryArguments,
    cacheTTL: Duration,
    override val getConnection: () => Connection,
    override val getTimeMeasurement: () => AsyncExecutionTimeMeasurement,
    params: Params
) extends DatabaseEnricherInvoker(
      query,
      argsCount,
      tableDef,
      strategy,
      queryArgumentsExtractor,
      getConnection,
      getTimeMeasurement,
      params
    ) {

  import DatabaseEnricherInvokerWithCache._

  // TODO: cache size
  private val cache: AsyncCache[CacheKey, CacheEntry[queryExecutor.QueryResult]] = Caffeine
    .newBuilder()
    .expireAfterWrite(cacheTTL)
    .buildAsync[CacheKey, CacheEntry[queryExecutor.QueryResult]]

  import scala.compat.java8.FutureConverters._

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      componentUseCase: ComponentUseCase,
      nodeDeploymentData: NodeDeploymentData,
  ): Future[queryExecutor.QueryResult] = {
    getTimeMeasurement().measuring {
      val queryArguments = queryArgumentsExtractor(argsCount, params, context)
      val cacheKey       = CacheKey(query, queryArguments)

      cache
        .get(
          cacheKey,
          (k, unused) => {
            // we use our own executor
            queryDatabase(k.queryArguments).map(CacheEntry(_)).toJava.toCompletableFuture
          }
        )
        .toScala
        .map(_.value)
    }
  }

}
