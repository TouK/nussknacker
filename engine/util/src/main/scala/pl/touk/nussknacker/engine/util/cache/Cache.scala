package pl.touk.nussknacker.engine.util.cache

import scala.concurrent.duration.Duration.{Inf, Undefined}
import scala.concurrent.duration.{Duration, FiniteDuration}

trait Cache[K, V] {
  def getOrCreate(key: K)(value: => V): V
}

trait ExpiryConfig[-K, -V] {
  def expireAfterAccessFn(k: K, v: V): Duration = Undefined
  def expireAfterWriteFn(k: K, v: V): Duration = Inf
}

case class FixedExpiryConfig(expireAfterAccess: Option[FiniteDuration] = None,
                             expireAfterWrite: Option[FiniteDuration] = None) extends ExpiryConfig[Any, Any] {
  override def expireAfterAccessFn(k: Any, v: Any): Duration = expireAfterAccess.getOrElse(Undefined)
  override def expireAfterWriteFn(k: Any, v: Any): Duration = expireAfterWrite.getOrElse(Inf)
}

case class CacheConfig[-K, -V](maximumSize: Long, expiry: ExpiryConfig[K, V])

object CacheConfig {
  val defaultMaximumSize = 10000L

  /**
   * @param maximumSize the maximum elements number can contain cache
   * @param expireAfterAccess the expiration time from last action (read / write)
   * @param expireAfterWrite the expiration time after value was written to cache
   */
  def apply[K, V](maximumSize: Long = CacheConfig.defaultMaximumSize,
                  expireAfterAccess: Option[FiniteDuration] = None,
                  expireAfterWrite: Option[FiniteDuration] = None): CacheConfig[K, V] =
    new CacheConfig(maximumSize, FixedExpiryConfig(expireAfterAccess, expireAfterWrite))

  def apply[K, V](expiry: ExpiryConfig[K, V]): CacheConfig[K, V] =
    new CacheConfig(defaultMaximumSize, expiry)

}
