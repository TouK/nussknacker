package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.{Caffeine, Expiry, Ticker}

import scala.concurrent.duration.{Deadline, FiniteDuration, NANOSECONDS}

private object DefaultCacheBuilder {

  implicit class ConfiguredExpiry[K, V](config: ExpiryConfig[K, V]) extends Expiry[K, V] {

    override def expireAfterCreate(key: K, value: V, currentTime: Long): Long =
      expireOrOverrideByInfiniteCurrentDuration(config.expireAfterWriteFn(key, value, Deadline(currentTime, NANOSECONDS)), currentTime)

    override def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      expireAfterCreate(key, value, currentTime)

    override def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      expireOrPreserveCurrentDuration(config.expireAfterAccessFn(key, value, Deadline(currentTime, NANOSECONDS)), currentTime, currentDuration)

    private def expireOrOverrideByInfiniteCurrentDuration(expirationDeadline: Option[Deadline], currentTime: Long): Long =
      expirationDeadline.map(_.time.toNanos - currentTime).getOrElse(Long.MaxValue)

    private def expireOrPreserveCurrentDuration(expirationDeadline: Option[Deadline], currentTime: Long, currentDuration: Long): Long =
      expirationDeadline.map(_.time.toNanos - currentTime).getOrElse(currentDuration)

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
    val result = underlying.get(key, asJavaFunction((_: K) => value))
    // we must do get to make sure that read expiration will be respected
    underlying.getIfPresent(key)
    result
  }

  override def get(key: K): Option[V] = Option(underlying.getIfPresent(key))

  override def put(key: K)(value: V): Unit = underlying.put(key, value)
}

class SingleValueCache[T](expireAfterAccess: Option[FiniteDuration], expireAfterWrite: Option[FiniteDuration]) {

  private val cache = new DefaultCache[Unit, T](CacheConfig(1, expireAfterAccess, expireAfterWrite))

  def getOrCreate(value: => T): T = cache.getOrCreate(())(value)

  def get(): Option[T] = cache.get(())

  def put(value: T): Unit = cache.put(())(value)
}