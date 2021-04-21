package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryError}

import java.util.regex.Pattern

trait TopicSelectionStrategy {

  val schemaRegistryClient: SchemaRegistryClient

  def getTopics: Validated[SchemaRegistryError, List[String]]

}

class AllTopicsSelectionStrategy(val schemaRegistryClient: SchemaRegistryClient) extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[String]] = schemaRegistryClient.getAllTopics

}

class TopicPatternSelectionStrategy(val schemaRegistryClient: SchemaRegistryClient, val topicPattern: Pattern)
  extends TopicSelectionStrategy {

  override def getTopics: Validated[SchemaRegistryError, List[String]] =
    schemaRegistryClient.getAllTopics.map(_.filter(topicPattern.matcher(_).matches()))

}
