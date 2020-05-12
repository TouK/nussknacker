package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache
import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.duration.Duration

/**
  * @param maximumSize the maximum elements number can contain cache
  * @param expireAfterAccess the expiration time from last action (read / write)
  * @tparam T
  */
class DefaultCache[T](maximumSize: Long, expireAfterAccess: Option[Duration]) extends Cache[T] {

  import scala.compat.java8.FunctionConverters._

  private lazy val underlying: cache.Cache[String, T] = {
    val builder = Caffeine
      .newBuilder()
      .asInstanceOf[Caffeine[String, T]]

    builder
      .maximumSize(maximumSize)
      .weakValues()

    expireAfterAccess
      .foreach(expire => builder.expireAfterAccess(java.time.Duration.ofMillis(expire.toMillis)))

    builder
      .build[String, T]
  }

  override def getOrCreate(key: String)(value: => T): T =
    underlying.get(key, asJavaFunction(k => value))
}

object DefaultCache {

  val defaultMaximumSize: Long = 10000L

  def apply[T](): DefaultCache[T] =
    new DefaultCache(defaultMaximumSize, Option.empty)

  def apply[T](expireAfterAccess: Option[Duration]): DefaultCache[T] =
    new DefaultCache(defaultMaximumSize, expireAfterAccess)
}
