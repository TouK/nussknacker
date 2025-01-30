package pl.touk.nussknacker.ui.util

import akka.actor.{ActorSystem, Cancellable}
import cats.effect.{Resource, Sync}
import com.github.benmanes.caffeine.cache.Cache
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.ui.util.AutoRefreshableCache.{AutoRefreshableCacheConfig, CacheItem}
import pl.touk.nussknacker.ui.utils.GenericCaffeineCache

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.language.higherKinds

// Cache with support for auto-refreshing stored values:
// - instead of storing simple map of Map[KEY, VALUE], it stores `Map[KEY, (CURRENT_VALUE, VALUE_CREATOR)]`
// - once a key is put into cache
//   - value that corresponds to that key will be refreshed with `config.autoRefreshInterval` interval using VALUE_CREATOR
//   - the auto-refresh will be performed for `config.autoRefreshDurationSinceLastUsage` since the cache was last queried about the key
class AutoRefreshableCache[KEY, VALUE](
    actorSystem: ActorSystem,
    config: AutoRefreshableCacheConfig,
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  private val keysToRefresh: Cache[KEY, Unit] =
    new GenericCaffeineCache[KEY, Unit](
      java.time.Duration.ofMillis(config.autoRefreshDurationSinceLastUsage.toMillis)
    ).getCache

  private val cache: Cache[KEY, CacheItem[VALUE]] =
    new GenericCaffeineCache[KEY, CacheItem[VALUE]](
      java.time.Duration.ofMillis(2 * config.autoRefreshInterval.toMillis)
    ).getCache

  def getIfPresentOrPut(key: KEY, updater: () => Future[VALUE]): Future[VALUE] = {
    keysToRefresh.put(key, ())
    Option(cache.getIfPresent(key)) match {
      case Some(CacheItem(currentValue, _, valid)) if valid =>
        logger.debug(s"Value present in AutoRefreshableCache with key: $key")
        Future.successful(currentValue)
      case None | Some(_) =>
        updater().map { value =>
          logger.debug(s"Putting new value into AutoRefreshableCache with key: $key")
          cache.put(key, CacheItem(value, updater, valid = true))
          value
        }
    }
  }

  def invalidateAll(): Unit = {
    cache.asMap().forEach { case (key, value) => (key, value.copy(valid = false)) }
  }

  @volatile private var scheduledJob: Option[Cancellable] = None

  private def start(): Unit = {
    scheduledJob = Some(
      actorSystem.scheduler.scheduleAtFixedRate(config.autoRefreshInterval, config.autoRefreshInterval) { () =>
        logger.debug(s"AutoRefreshableCache refresh triggered")
        val currentCacheContent = cache.asMap().asScala
        Await.result(
          Future
            .sequence(
              currentCacheContent.map { case (key, cacheItem) =>
                Option(keysToRefresh.getIfPresent(key)) match {
                  case Some(_) =>
                    cacheItem.valueCreator().map { newValue =>
                      logger.debug(s"AutoRefreshableCache refreshed for key $key")
                      cache.put(key, CacheItem(newValue, cacheItem.valueCreator, valid = true))
                    }
                  case None =>
                    logger.debug(s"Auto-refresh duration expired for $key")
                    cache.invalidate(key)
                    Future.unit
                }
              }
            )
            .map(_ => ()),
          config.autoRefreshInterval,
        )
      }
    )
  }

  private def close(): Unit = {
    scheduledJob.map(_.cancel())
  }

}

object AutoRefreshableCache {

  final case class CacheItem[VALUE](value: VALUE, valueCreator: () => Future[VALUE], valid: Boolean)

  def create[M[_]: Sync, K, V](
      actorSystem: ActorSystem,
      config: AutoRefreshableCacheConfig,
  )(implicit executionContext: ExecutionContext): Resource[M, AutoRefreshableCache[K, V]] = {
    Resource.make(
      acquire = {
        Sync[M].delay {
          val service = new AutoRefreshableCache[K, V](actorSystem, config)
          service.start()
          service
        }
      }
    )(release = cache => Sync[M].delay(cache.close()))
  }

  final case class AutoRefreshableCacheConfig(
      autoRefreshInterval: FiniteDuration = 30 seconds,
      autoRefreshDurationSinceLastUsage: FiniteDuration = 1 hour,
  )

  object AutoRefreshableCacheConfig {

    def parse(config: Config, path: String): AutoRefreshableCacheConfig =
      config
        .getAs[AutoRefreshableCacheConfig](path)
        .getOrElse(AutoRefreshableCacheConfig())

  }

}
