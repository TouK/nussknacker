package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import scala.collection.mutable

object KafkaAdminCachesFactory extends KafkaAdminCachesFactory

class KafkaAdminCachesFactory {
  @transient private lazy val caches: mutable.Map[KafkaConfig, KafkaAdminCaches] = mutable.Map()

  def create(kafkaConfig: KafkaConfig): KafkaAdminCaches = synchronized {
    caches.getOrElseUpdate(kafkaConfig, new KafkaAdminCaches(kafkaConfig.kafkaAdminConfig.cacheConfig))
  }

}

class KafkaAdminCaches(cacheConfig: KafkaAdminCacheConfig) {

  val topicsCache = new SingleValueCache[Set[UnspecializedTopicName]](
    expireAfterAccess = None,
    expireAfterWrite = Some(cacheConfig.topicsExpirationTime)
  )

  val autoCreateTopicSettingCache = new SingleValueCache[Boolean](
    expireAfterAccess = None,
    expireAfterWrite = Some(cacheConfig.autoCreateTopicSettingExpirationTime)
  )

}
