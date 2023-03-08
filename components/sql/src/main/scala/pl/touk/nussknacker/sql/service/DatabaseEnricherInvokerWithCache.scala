package pl.touk.nussknacker.sql.service

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.ContextId
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

  case class CacheKey(query: String, queryArguments: QueryArguments)
  case class CacheEntry[+A](value: A)
}

class DatabaseEnricherInvokerWithCache(query: String,
                                       argsCount: Int,
                                       tableDef: TableDefinition,
                                       strategy: QueryResultStrategy,
                                       queryArgumentsExtractor: (Int, Map[String, Any]) => QueryArguments,
                                       cacheTTL: Duration,
                                       override val returnType: typing.TypingResult,
                                       override val getConnection: () => Connection,
                                       override val getTimeMeasurement: () => AsyncExecutionTimeMeasurement) extends DatabaseEnricherInvoker(query, argsCount, tableDef, strategy, queryArgumentsExtractor, returnType, getConnection, getTimeMeasurement) {
  import DatabaseEnricherInvokerWithCache._

  // TODO: cache size
  private val cache = Caffeine.newBuilder()
    .expireAfterWrite(cacheTTL)
    .buildAsync[CacheKey, CacheEntry[queryExecutor.QueryResult]]

  import scala.compat.java8.FutureConverters._

  override def invokeService(params: Map[String, Any])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId, componentUseCase: ComponentUseCase): Future[queryExecutor.QueryResult] = {
    getTimeMeasurement().measuring {
      val queryArguments = queryArgumentsExtractor(argsCount, params)
      val cacheKey = CacheKey(query, queryArguments)
      Option(cache.getIfPresent(cacheKey)).map(_.toScala.map(_.value)).getOrElse {
        val queryResult: Future[queryExecutor.QueryResult] = queryDatabase(queryArguments)
        cache.put(cacheKey, queryResult.map(CacheEntry(_)).toJava.toCompletableFuture)
        queryResult
      }
    }
  }
}
