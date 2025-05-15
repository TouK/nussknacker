package pl.touk.nussknacker.engine.kafka

import com.sun.org.apache.xalan.internal.lib.ExsltDatetime.time
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.Admin

private object LazyKafkaAdminClientCache {
  val instance = new LazyKafkaAdminClientCache
}

private class LazyKafkaAdminClientCache extends LazyLogging {
  private type CacheKey = KafkaConfig

  private case class CacheValue(client: Admin, usedCount: Int) {
    def incrementUsage: CacheValue = copy(usedCount = usedCount + 1)
    def decrementUsage: CacheValue = copy(usedCount = usedCount - 1)
  }

  private var cache: Map[CacheKey, CacheValue] = Map.empty

  def getOrCreate(kafkaConfig: KafkaConfig)(create: => Admin): Admin = synchronized {
    cache.get(kafkaConfig) match {
      case Some(cacheValue) =>
        cache += (kafkaConfig -> cacheValue.incrementUsage)
        cacheValue.client
      case None =>
        val newClient = create
        cache += (kafkaConfig -> CacheValue(newClient, usedCount = 1))
        newClient
    }
  }

  def close(kafkaConfig: KafkaConfig): Unit = synchronized {
    cache.get(kafkaConfig) match {
      case Some(cacheValue) if cacheValue.usedCount == 1 =>
        logger.info(s"Closing Kafka client for config: $kafkaConfig")
        cacheValue.client.close(java.time.Duration.ofMillis(KafkaUtils.defaultTimeoutMillis))
        cache -= kafkaConfig
      case Some(cacheValue) =>
        logger.info(s"Closing Kafka client for config: $kafkaConfig, but it is still used by others")
        cache += (kafkaConfig -> cacheValue.decrementUsage)
      case None =>
        logger.warn("Trying to close already closed client")
    }
  }

}

class LazyKafkaAdminClient private[kafka] (cache: LazyKafkaAdminClientCache, kafkaConfig: KafkaConfig, create: => Admin)
    extends AutoCloseable
    with LazyLogging {

  private lazy val client = cache.getOrCreate(kafkaConfig)(create)

  @volatile private var closed = false

  def getOrCreate: Admin = client

  override def close(): Unit = synchronized {
    if (!closed) {
      cache.close(kafkaConfig)
      closed = true
      logger.info(s"Client for config: $kafkaConfig marked as closed in this instance")
    } else {
      logger.debug(s"Client for config: $kafkaConfig already closed in this instance")
    }
  }

}
