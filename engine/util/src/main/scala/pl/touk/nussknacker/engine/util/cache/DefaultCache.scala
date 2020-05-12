package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.ConcurrentStatsCounter
import scalacache.Entry

import scala.concurrent.duration.Duration

/**
  * @param maximumSize the maximum elements number can contain cache
  * @param expireAfterAccess the expiration time from last action (read / write)
  * @tparam T
  */
class DefaultCache[T](maximumSize: Long, expireAfterAccess: Option[Duration], registerStats: Boolean) extends Cache[T] {

  import scalacache.caffeine._
  import scalacache.modes.sync._

  import scala.concurrent.duration._

  private val stats = new ConcurrentStatsCounter

  private lazy val caffeineClientCache = {
    val builder = Caffeine
      .newBuilder()
      .maximumSize(maximumSize)
      .weakValues()

    expireAfterAccess.foreach(expire => builder.expireAfterAccess(java.time.Duration.ofMillis(expire.toMillis)))

    builder.build[String, Entry[T]]
  }

  implicit private val cache: CaffeineCache[T] =
    CaffeineCache(caffeineClientCache)

  override def getOrCreate(key: String, ttl: Option[Duration])(value: => T): T = {
    if (ttl.exists(time => expireAfterAccess.exists(_.lt(time)))) {
      throw new IllegalArgumentException(s"TTL (${ttl.get}) should be lower than ExpireAfterAccess (${expireAfterAccess.get}).")
    }

    cache
      .doGet(key)
      .map(readProcessing)
      .getOrElse(cacheProcessing(key, value, ttl))
  }

  def missCount: Long =
    stats.snapshot().missCount()

  def hitCount: Long =
    stats.snapshot().hitCount()

  private def readProcessing(value: T): T = {
    if (registerStats) {
      stats.recordHits(1)
    }
    value
  }

  private def cacheProcessing(key: String, value: T, ttl: Option[Duration]): T = {
    cache.doPut(key, value, ttl)
    if (registerStats) {
      stats.recordMisses(1)
    }
    value
  }
}

object DefaultCache {

  val defaultMaximumSize: Long = 10000L

  def apply[T](): DefaultCache[T] =
    new DefaultCache(defaultMaximumSize, Option.empty, registerStats = false)

  def apply[T](maximumSize: Long, expireAfterAccess: Option[Duration]): DefaultCache[T] =
    new DefaultCache(maximumSize, expireAfterAccess, registerStats = false)
}
