package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.duration.FiniteDuration

class DefaultCache[K, V](cacheConfig: CacheConfig) extends Cache[K, V] {

  import scala.compat.java8.FunctionConverters._

  private lazy val underlying: cache.Cache[K, V] = {
    val builder = Caffeine
      .newBuilder()
      .asInstanceOf[Caffeine[K, V]]

    builder
      .maximumSize(cacheConfig.maximumSize)
      .weakValues()

    cacheConfig.expireAfterAccess
      .foreach(expire => builder.expireAfterAccess(java.time.Duration.ofMillis(expire.toMillis)))

    cacheConfig.expireAfterWrite
      .foreach(expire => builder.expireAfterWrite(java.time.Duration.ofMillis(expire.toMillis)))

    builder
      .build[K, V]
  }

  override def getOrCreate(key: K)(value: => V): V =
    underlying.get(key, asJavaFunction(k => value))
}

object DefaultCache {

  def apply[K, V](cacheConfig: CacheConfig = CacheConfig()): Cache[K, V] =
    new DefaultCache[K, V](cacheConfig)

}

class SingleValueCache[T](expireAfterAccess: Option[FiniteDuration], expireAfterWrite: Option[FiniteDuration]) {

  private val cache = new DefaultCache[Unit.type, T](CacheConfig(1, expireAfterAccess, expireAfterWrite))

  def getOrCreate(value: => T): T = cache.getOrCreate(Unit)(value)

}