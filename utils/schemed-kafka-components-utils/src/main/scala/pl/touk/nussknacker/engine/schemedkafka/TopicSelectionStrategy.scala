package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.regex.Pattern

trait TopicSelectionStrategy extends Serializable {

  def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]]

  def filterTopics(topics: List[UnspecializedTopicName]): List[UnspecializedTopicName]

}

class AllTopicsSelectionStrategy extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    schemaRegistryClient.getAllTopics

  override def filterTopics(topics: List[UnspecializedTopicName]): List[UnspecializedTopicName] = topics

}

class TopicPatternSelectionStrategy(val topicPattern: Pattern) extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UnspecializedTopicName]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topic => topicPattern.matcher(topic.name).matches()))

  override def filterTopics(topics: List[UnspecializedTopicName]): List[UnspecializedTopicName] =
    topics.filter(topic => topicPattern.matcher(topic.name).matches())

}
