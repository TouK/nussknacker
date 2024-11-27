package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.common.errors.TimeoutException
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.concurrent.ExecutionException
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

trait TopicSelectionStrategy extends Serializable {

  def getTopics(
      schemaRegistryClient: SchemaRegistryClient,
      kafkaConfig: KafkaConfig
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]]

}

class TopicsWithExistingSubjectSelectionStrategy extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient,
      kafkaConfig: KafkaConfig
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    schemaRegistryClient.getAllTopics
  }

}

class AllNonHiddenTopicsSelectionStrategy extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient,
      kafkaConfig: KafkaConfig
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val topicsFromSchemaRegistry = schemaRegistryClient.getAllTopics

    val schemaLessTopics: List[UnspecializedTopicName] = {
      try {
        KafkaUtils.usingAdminClient(kafkaConfig) {
          _.listTopics(new ListTopicsOptions().timeoutMs(5000))
            .names()
            .get()
            .asScala
            .toSet
            .map(UnspecializedTopicName.apply)
            .filterNot(topic => topic.name.startsWith("_"))
            .toList
        }
      } catch {
        // In some tests we pass dummy kafka address, so when we try to get topics from kafka it fails
        case err: ExecutionException =>
          err.getCause match {
            case _: TimeoutException => List.empty
            case _                   => throw err
          }
      }
    }

    topicsFromSchemaRegistry.map(topics => (topics ++ schemaLessTopics).distinct)
  }

}

class TopicsMatchingPatternWithExistingSubjectsSelectionStrategy(val topicPattern: Pattern)
    extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient,
      kafkaConfig: KafkaConfig
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topic => topicPattern.matcher(topic.name).matches()))

}
