package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.{Caffeine, Expiry, Ticker}

import scala.concurrent.duration.Duration.Undefined
import scala.concurrent.duration.FiniteDuration

class DefaultCache[K, V](cacheConfig: CacheConfig[K, V], ticker: Ticker = Ticker.systemTicker()) extends Cache[K, V] {

  import scala.compat.java8.FunctionConverters._

  implicit class ConfiguredExpiry(config: ExpiryConfig[K, V]) extends Expiry[K, V] {

    override def expireAfterCreate(key: K, value: V, currentTime: Long): Long =
      Some(config.expireAfterWriteFn(key, value)).filter(_.isFinite()).map(_.toNanos).getOrElse(Long.MaxValue)

    override def expireAfterUpdate(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      expireAfterCreate(key, value, currentTime)

    override def expireAfterRead(key: K, value: V, currentTime: Long, currentDuration: Long): Long =
      Some(config.expireAfterAccessFn(key, value)).filterNot(_ eq Undefined).map(_.toNanos).getOrElse(currentDuration)
  }

  private lazy val underlying: cache.Cache[K, V] = {
    val builder = Caffeine
      .newBuilder()
      .ticker(ticker)
      .asInstanceOf[Caffeine[K, V]]
      .maximumSize(cacheConfig.maximumSize)
      .expireAfter(cacheConfig.expiry)

    builder
      .build[K, V]
  }

  override def getOrCreate(key: K)(value: => V): V =
    underlying.get(key, asJavaFunction(k => value))
}

object DefaultCache {

  def apply[K, V](cacheConfig: CacheConfig[K, V] = CacheConfig()): Cache[K, V] =
    new DefaultCache[K, V](cacheConfig)

}

class SingleValueCache[T](expireAfterAccess: Option[FiniteDuration], expireAfterWrite: Option[FiniteDuration]) {

  private val cache = DefaultCache[Unit.type, T](CacheConfig(1, expireAfterAccess, expireAfterWrite))

  def getOrCreate(value: => T): T = cache.getOrCreate(Unit)(value)

}