package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TimeoutException
import pl.touk.nussknacker.engine.api.util.ExceptionUtils
import pl.touk.nussknacker.engine.kafka.{CachingKafkaAdminClient, KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.regex.Pattern

trait TopicSelectionStrategy extends Serializable {

  def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]]

}

class TopicsWithExistingSubjectSelectionStrategy(schemaRegistryClient: SchemaRegistryClient)
    extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    schemaRegistryClient.getAllTopics
  }

}

object AllNonHiddenTopicsSelectionStrategy {
  def apply(schemaRegistryClient: SchemaRegistryClient, kafkaConfig: KafkaConfig): AllNonHiddenTopicsSelectionStrategy =
    new AllNonHiddenTopicsSelectionStrategy(schemaRegistryClient, KafkaUtils.createCachingAdminClient(kafkaConfig))
}

class AllNonHiddenTopicsSelectionStrategy(
    schemaRegistryClient: SchemaRegistryClient,
    cachingKafkaAdminClient: CachingKafkaAdminClient
) extends TopicSelectionStrategy
    with LazyLogging {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val topicsFromSchemaRegistry = schemaRegistryClient.getAllTopics

    val schemaLessTopics: List[UnspecializedTopicName] = {
      try {
        // Since this validator is used to provide possible values for the topic parameter, it must always fetch fresh topics from Kafka
        // to ensure newly created topics can be selected.
        cachingKafkaAdminClient.fetchFreshTopicsAndCache
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
