package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import pl.touk.nussknacker.engine.kafka.UncategorizedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.regex.Pattern

trait TopicSelectionStrategy extends Serializable {

  def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UncategorizedTopicName]]

}

class AllTopicsSelectionStrategy extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UncategorizedTopicName]] =
    schemaRegistryClient.getAllTopics

}

class TopicPatternSelectionStrategy(val topicPattern: Pattern) extends TopicSelectionStrategy {

  override def getTopics(
      schemaRegistryClient: SchemaRegistryClient
  ): Validated[SchemaRegistryError, List[UncategorizedTopicName]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topic => topicPattern.matcher(topic.name).matches()))

}
