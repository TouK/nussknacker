package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TimeoutException
import pl.touk.nussknacker.engine.api.util.ExceptionUtils
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

trait TopicSelectionStrategy extends Serializable {

  def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]]

}

class TopicsWithExistingSubjectSelectionStrategy(schemaRegistryClient: SchemaRegistryClient)
    extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    schemaRegistryClient.getAllTopics
  }

}

class AllNonHiddenTopicsSelectionStrategy(schemaRegistryClient: SchemaRegistryClient, kafkaConfig: KafkaConfig)
    extends TopicSelectionStrategy
    with LazyLogging {

  private val strategyConfig = kafkaConfig.topicsWithoutSchemaConfig

  @transient private lazy val topicsCache = new SingleValueCache[Set[UnspecializedTopicName]](
    expireAfterAccess = None,
    expireAfterWrite = Some(strategyConfig.topicsFetchCacheTtl)
  )

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val topicsFromSchemaRegistry = schemaRegistryClient.getAllTopics

    val schemaLessTopics: List[UnspecializedTopicName] = {
      try {
        val allTopics = topicsCache.getOrCreate {
          KafkaUtils.usingAdminClient(kafkaConfig) {
            _.listTopics(new ListTopicsOptions().timeoutMs(strategyConfig.topicsFetchTimeout.toMillis.toInt))
              .names()
              .get()
              .asScala
              .toSet
              .map(UnspecializedTopicName.apply)
          }
        }
        allTopics
          .filterNot(topic => topic.name.startsWith("_"))
          .toList
      } catch {
        // In some tests we pass dummy kafka address, so when we try to get topics from kafka it fails
        case err if ExceptionUtils.unwrapCommonWrappingExceptions(err).isInstanceOf[TimeoutException] =>
          List.empty
        case ex: KafkaException =>
          logger.error("Kafka exception while getting topics", ex)
          List.empty
      }
    }

    topicsFromSchemaRegistry.map(topics => (topics ++ schemaLessTopics).distinct)
  }

}

class TopicsMatchingPatternWithExistingSubjectsSelectionStrategy(
    val topicPattern: Pattern,
    schemaRegistryClient: SchemaRegistryClient
) extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topic => topicPattern.matcher(topic.name).matches()))

}
