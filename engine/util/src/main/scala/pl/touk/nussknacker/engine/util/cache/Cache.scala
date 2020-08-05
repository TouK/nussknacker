package pl.touk.nussknacker.engine.util.cache

import scala.concurrent.duration.FiniteDuration

trait Cache[K, V] {
  def getOrCreate(key: K)(value: => V): V
}

/**
 * @param maximumSize the maximum elements number can contain cache
 * @param expireAfterAccess the expiration time from last action (read / write)
 * @param expireAfterWrite the expiration time after value was written to cache
 */
case class CacheConfig(maximumSize: Long = CacheConfig.defaultMaximumSize,
                       expireAfterAccess: Option[FiniteDuration] = None,
                       expireAfterWrite: Option[FiniteDuration] = None)

object CacheConfig {
  val defaultMaximumSize = 10000L
}