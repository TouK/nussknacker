package pl.touk.nussknacker.engine.util.cache

import java.util.concurrent.Executor

import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.{Caffeine, Expiry, Ticker}

import scala.compat.java8.FunctionConverters.asJavaBiFunction
import scala.concurrent.duration.{Deadline, FiniteDuration, NANOSECONDS}
import scala.concurrent.{ExecutionContext, Future}

private object DefaultCacheBuilder {

  implicit class ConfiguredExpiry[K, V](config: ExpiryConfig[K, V]) extends Expiry[K, V] {

    override def expireAfterCreate(key: K, value: V, currentTime: Long): Long =
      config.expireAfterWriteFn(key, value, Deadline(currentTime, NANOSECONDS))
        .map(_.time.toNanos - currentTime).getOrElse(Long.MaxValue)

    override def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      expireAfterCreate(key, value, currentTime)

    override def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      config.expireAfterAccessFn(key, value, Deadline(currentTime, NANOSECONDS))
        .map(_.time.toNanos - currentTime).getOrElse(currentDuration)
  }

  def apply[K, V](cacheConfig: CacheConfig[K, V], ticker: Ticker = Ticker.systemTicker()): Caffeine[K, V] = {
    Caffeine
      .newBuilder()
      .ticker(ticker)
      .asInstanceOf[Caffeine[K, V]]
      .maximumSize(cacheConfig.maximumSize)
      .expireAfter(cacheConfig.expiry)
  }
}

class DefaultCache[K, V](cacheConfig: CacheConfig[K, V], ticker: Ticker = Ticker.systemTicker()) extends Cache[K, V] {
  private lazy val underlying: cache.Cache[K, V] =
    DefaultCacheBuilder(cacheConfig, ticker).build()

  override def getOrCreate(key: K)(value: => V): V = {
    import scala.compat.java8.FunctionConverters._
    underlying.get(key, asJavaFunction((_: K) => value))
  }
}

class DefaultAsyncCache[K, V](cacheConfig: CacheConfig[K, V], ticker: Ticker = Ticker.systemTicker())(implicit ec: ExecutionContext) extends AsyncCache[K, V] {
  private lazy val underlying: cache.AsyncCache[K, V] =
    DefaultCacheBuilder(cacheConfig, ticker)
      .executor(new Executor {
        override def execute(runnable: Runnable): Unit = ec.execute(runnable)
      })
      .buildAsync()

  override def getOrCreate(key: K)(value: => Future[V]): Future[V] = {
    import scala.compat.java8.FunctionConverters._
    import scala.compat.java8.FutureConverters._
    underlying.get(key, asJavaBiFunction((_: K, _: Executor) => {
      value.toJava.toCompletableFuture
    })).toScala
  }

  override def put(key: K)(value: Future[V]): Unit = {
    import scala.compat.java8.FutureConverters._
    underlying.put(key, value.toJava.toCompletableFuture)
  }
}

class SingleValueCache[T](expireAfterAccess: Option[FiniteDuration], expireAfterWrite: Option[FiniteDuration]) {

  private val cache = new DefaultCache[Unit.type, T](CacheConfig(1, expireAfterAccess, expireAfterWrite))

  def getOrCreate(value: => T): T = cache.getOrCreate(Unit)(value)

}