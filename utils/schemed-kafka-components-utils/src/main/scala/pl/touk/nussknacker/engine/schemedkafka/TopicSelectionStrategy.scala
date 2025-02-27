package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import org.apache.kafka.clients.admin.{Admin, ListTopicsOptions}
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.TimeoutException
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.concurrent.ExecutionException
import java.util.regex.Pattern
import scala.concurrent.duration.FiniteDuration
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

// TODO: Close client
class AllNonHiddenTopicsSelectionStrategy(
    schemaRegistryClient: SchemaRegistryClient,
    kafkaAdminClient: Admin,
    fetchTimeout: FiniteDuration
) extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] = {
    val topicsFromSchemaRegistry = schemaRegistryClient.getAllTopics

    val schemaLessTopics: List[UnspecializedTopicName] = {
      try {
        kafkaAdminClient
          .listTopics(new ListTopicsOptions().timeoutMs(fetchTimeout.toMillis.toInt))
          .names()
          .get()
          .asScala
          .toSet
          .map(UnspecializedTopicName.apply)
          .filterNot(topic => topic.name.startsWith("_"))
          .toList
      } catch {
        // In some tests we pass dummy kafka address, so when we try to get topics from kafka it fails
        case err: ExecutionException =>
          err.getCause match {
            case _: TimeoutException => List.empty
            case _                   => throw err
          }
        case _: KafkaException =>
          List.empty
      }
    }

    topicsFromSchemaRegistry.map(topics => (topics ++ schemaLessTopics).distinct)
  }

}

class TopicsMatchingPatternWithExistingSubjectsSelectionStrategy(
    schemaRegistryClient: SchemaRegistryClient,
    topicPattern: Pattern
) extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topic => topicPattern.matcher(topic.name).matches()))

}
