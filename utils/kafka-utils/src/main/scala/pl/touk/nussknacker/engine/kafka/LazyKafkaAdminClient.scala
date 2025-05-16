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
        logger.info(
          s"Reusing existing client for: ${kafkaConfig.kafkaBootstrapServers}, usages of this client so far: ${cacheValue.usedCount}"
        )
        cache += (kafkaConfig -> cacheValue.incrementUsage)
        cacheValue.client
      case None =>
        logger.info(
          s"Creating new client for: ${kafkaConfig.kafkaBootstrapServers}, unique clients so far: ${cache.size}"
        )
        val newClient = create
        cache += (kafkaConfig -> CacheValue(newClient, usedCount = 1))
        newClient
    }
  }

  def close(kafkaConfig: KafkaConfig): Unit = synchronized {
    cache.get(kafkaConfig) match {
      case Some(cacheValue) if cacheValue.usedCount == 1 =>
        logger.info(s"Closing client for: ${kafkaConfig.kafkaBootstrapServers}, unique clients so far: ${cache.size}")
        try {
          cacheValue.client.close(java.time.Duration.ofMillis(KafkaUtils.defaultTimeoutMillis))
        } finally {
          cache -= kafkaConfig
        }
      case Some(cacheValue) =>
        logger.info(
          s"Decrementing client usage for: ${kafkaConfig.kafkaBootstrapServers}, usages of this client so far: ${cacheValue.usedCount}"
        )
        cache += (kafkaConfig -> cacheValue.decrementUsage)
      case None =>
        logger.warn(s"Trying to close already closed or never opened client for: ${kafkaConfig.kafkaBootstrapServers}")
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
    require(initializationState == InitializationState.NotInitialized)
    val admin = cache.getOrCreate(kafkaConfig)(create)
    initializationState = InitializationState.Opened
    admin
  }

  def getOrCreate: Admin = client

  // TODO: In fact we never close the client, we would need to add a lifecycle to components created by component providers.
  // To mitigate having too many client instance they are cached by unique Kafka config.
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
        logger.warn(s"Client for: ${kafkaConfig.kafkaBootstrapServers} already closed in this instance")
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
