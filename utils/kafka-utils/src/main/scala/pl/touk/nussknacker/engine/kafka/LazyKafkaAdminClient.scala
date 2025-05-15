package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.Admin
import pl.touk.nussknacker.engine.kafka.LazyKafkaAdminClient.InitializationState

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
        logger.info(s"Reusing existing client for config: $kafkaConfig")
        cache += (kafkaConfig -> cacheValue.incrementUsage)
        cacheValue.client
      case None =>
        logger.info(s"Creating new client for config: $kafkaConfig")
        val newClient = create
        cache += (kafkaConfig -> CacheValue(newClient, usedCount = 1))
        newClient
    }
  }

  def close(kafkaConfig: KafkaConfig): Unit = synchronized {
    cache.get(kafkaConfig) match {
      case Some(cacheValue) if cacheValue.usedCount == 1 =>
        logger.info(s"Closing client for config: $kafkaConfig")
        try {
          cacheValue.client.close(java.time.Duration.ofMillis(KafkaUtils.defaultTimeoutMillis))
        } finally {
          cache -= kafkaConfig
        }
      case Some(cacheValue) =>
        logger.info(s"Closing client for config: $kafkaConfig, but it is still used by others")
        cache += (kafkaConfig -> cacheValue.decrementUsage)
      case None =>
        logger.warn(s"Trying to close already closed or never opened client for config: $kafkaConfig")
    }
  }

}

private object LazyKafkaAdminClientCache {
  val instance = new LazyKafkaAdminClientCache
}

class LazyKafkaAdminClient private[kafka] (cache: LazyKafkaAdminClientCache, kafkaConfig: KafkaConfig, create: => Admin)
    extends AutoCloseable
    with LazyLogging {

  @volatile private var initializationState: InitializationState = InitializationState.NotInitialized

  private lazy val client = {
    initializationState = InitializationState.Opened
    cache.getOrCreate(kafkaConfig)(create)
  }

  def getOrCreate: Admin = client

  override def close(): Unit = synchronized {
    initializationState match {
      case InitializationState.NotInitialized =>
        logger.info("Trying to close never used client")
      case InitializationState.Opened =>
        logger.info("Closing client")
        try {
          cache.close(kafkaConfig)
        } finally {
          initializationState = InitializationState.Closed
        }
      case InitializationState.Closed =>
        logger.warn(s"Client for config: $kafkaConfig already closed in this instance")
    }
  }

}

private object LazyKafkaAdminClient {
  sealed trait InitializationState

  object InitializationState {
    case object NotInitialized extends InitializationState
    case object Opened         extends InitializationState
    case object Closed         extends InitializationState
  }

}
