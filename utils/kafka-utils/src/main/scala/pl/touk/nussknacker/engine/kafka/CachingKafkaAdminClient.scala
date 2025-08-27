package pl.touk.nussknacker.engine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{Admin, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
import org.apache.kafka.common.config.ConfigResource
import pl.touk.nussknacker.engine.kafka.CachingKafkaAdminClient.UsingKafkaAdminClient

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class CachingKafkaAdminClient(
    caches: KafkaAdminCaches,
    adminClientTimeout: FiniteDuration,
    usingKafkaAdminClient: UsingKafkaAdminClient
) extends LazyLogging {

  def getCachedTopics: Option[Set[UnspecializedTopicName]] = {
    caches.topicsCache.get()
  }

  def fetchFreshTopicsAndCache: Set[UnspecializedTopicName] = {
    val topics = fetchFreshTopics
    caches.topicsCache.put(topics)
    topics
  }

  private def fetchFreshTopics: Set[UnspecializedTopicName] = {
    logger.debug("Fetching topics from Kafka")
    usingKafkaAdminClient { admin =>
      admin
        .listTopics(new ListTopicsOptions().timeoutMs(adminClientTimeout.toMillis.toInt))
        .names()
        .get()
        .asScala
        .toSet
        .map(UnspecializedTopicName.apply)
    }
  }

  def getOrFetchAutoCreateTopicsSetting: Boolean = {
    caches.autoCreateTopicSettingCache.getOrCreate {
      logger.debug("Fetching auto.create.topics.enable setting from Kafka")
      usingKafkaAdminClient { admin =>
        val randomKafkaNodeId = admin
          .describeCluster(new DescribeClusterOptions().timeoutMs(adminClientTimeout.toMillis.toInt))
          .nodes()
          .get()
          .asScala
          .head
          .id()
          .toString
        admin
          .describeConfigs(
            List(new ConfigResource(ConfigResource.Type.BROKER, randomKafkaNodeId)).asJava,
            new DescribeConfigsOptions().timeoutMs(adminClientTimeout.toMillis.toInt)
          )
          .values()
          .values()
          .asScala
          .map(_.get())
          .head // we ask for config of one node, but `describeConfigs` api have `List` of nodes, so here we got single element list
          .get("auto.create.topics.enable")
          .value()
          .toBoolean
      }
    }
  }

}

object CachingKafkaAdminClient {

  def apply(
      kafkaComponentsConfig: KafkaComponentsConfig,
      usingKafkaAdminClient: UsingKafkaAdminClient
  ): CachingKafkaAdminClient = {
    val caches = KafkaAdminCachesFactory.createOrRetrieve(kafkaComponentsConfig)
    new CachingKafkaAdminClient(caches, kafkaComponentsConfig.kafkaAdminConfig.clientTimeout, usingKafkaAdminClient)
  }

  trait UsingKafkaAdminClient {
    def apply[T](f: Admin => T): T
  }

}
