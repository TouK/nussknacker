package pl.touk.nussknacker.sql.service

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.ContextId
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.sql.db.query.{QueryArguments, QueryArgumentsExtractor, QueryResultStrategy}
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
                                       queryArgumentsExtractor: QueryArgumentsExtractor,
                                       cacheTTL: Duration,
                                       override val returnType: typing.TypingResult,
                                       override val getConnection: () => Connection) extends DatabaseEnricherInvoker(query, argsCount, tableDef, strategy, queryArgumentsExtractor, returnType, getConnection) {
  import DatabaseEnricherInvokerWithCache._

  // TODO: cache size
  private val cache = Caffeine.newBuilder()
    .expireAfterWrite(cacheTTL)
    .build[CacheKey, CacheEntry[queryExecutor.QueryResult]]

  override def invokeService(params: Map[String, Any])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId): Future[queryExecutor.QueryResult] = {
    val queryArguments = queryArgumentsExtractor(argsCount, params)
    val cacheKey = CacheKey(query, queryArguments)
    val result = Option(cache.getIfPresent(cacheKey)).map(_.value).getOrElse {
      val queryResult = queryDatabase(queryArguments)
      cache.put(cacheKey, CacheEntry(queryResult))
      queryResult
    }
    Future.successful(result)
  }
}
