package pl.touk.nussknacker.engine.util.cache

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, Duration, FiniteDuration}
import scala.concurrent.duration.Duration.{Inf, Undefined}

trait Cache[K, V] {
  def getOrCreate(key: K)(value: => V): V
  def get(key: K): Option[V]
  def put(key: K)(value: V): Unit
}

trait AsyncCache[K, V] {
  def getOrCreate(key: K)(value: => Future[V]): Future[V]
  // right now used only in tests
  def get(key: K): Option[V]
  def put(key: K)(value: Future[V]): Unit
}

trait ExpiryConfig[-K, -V] {
  def expireAfterAccessFn(k: K, v: V, now: Deadline): Option[Deadline] = None
  def expireAfterWriteFn(k: K, v: V, now: Deadline): Option[Deadline]  = None
}

case class FixedExpiryConfig(
    expireAfterAccess: Option[FiniteDuration] = None,
    expireAfterWrite: Option[FiniteDuration] = None
) extends ExpiryConfig[Any, Any] {
  override def expireAfterAccessFn(k: Any, v: Any, now: Deadline): Option[Deadline] = expireAfterAccess.map(now + _)
  override def expireAfterWriteFn(k: Any, v: Any, now: Deadline): Option[Deadline]  = expireAfterWrite.map(now + _)
}

case class CacheConfig[-K, -V](maximumSize: Long, expiry: ExpiryConfig[K, V])

object CacheConfig {
  val defaultMaximumSize = 10000L

  /**
   * @param maximumSize the maximum elements number can contain cache
   * @param expireAfterAccess the expiration time from last action (read / write)
   * @param expireAfterWrite the expiration time after value was written to cache
   */
  def apply(
      maximumSize: Long = CacheConfig.defaultMaximumSize,
      expireAfterAccess: Option[FiniteDuration] = None,
      expireAfterWrite: Option[FiniteDuration] = None
  ): CacheConfig[Any, Any] =
    new CacheConfig(maximumSize, FixedExpiryConfig(expireAfterAccess, expireAfterWrite))

  def apply[K, V](expiry: ExpiryConfig[K, V]): CacheConfig[K, V] =
    new CacheConfig(defaultMaximumSize, expiry)

}
