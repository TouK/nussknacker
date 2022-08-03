package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.regex.Pattern

trait TopicSelectionStrategy extends Serializable {

  def getTopics(schemaRegistryClient: SchemaRegistryClient): Validated[SchemaRegistryError, List[String]]

}

class AllTopicsSelectionStrategy extends TopicSelectionStrategy {

  override def getTopics(schemaRegistryClient: SchemaRegistryClient): Validated[SchemaRegistryError, List[String]] =
    schemaRegistryClient.getAllTopics

}

class TopicPatternSelectionStrategy(val topicPattern: Pattern)
  extends TopicSelectionStrategy {

  override def getTopics(schemaRegistryClient: SchemaRegistryClient): Validated[SchemaRegistryError, List[String]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topicPattern.matcher(_).matches()))

}
