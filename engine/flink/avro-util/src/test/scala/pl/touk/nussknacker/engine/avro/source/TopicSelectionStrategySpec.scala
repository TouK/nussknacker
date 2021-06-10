package pl.touk.nussknacker.engine.avro.source

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AllTopicsSelectionStrategy, TopicPatternSelectionStrategy}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory

import java.util.regex.Pattern

class TopicSelectionStrategySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  override protected val schemaRegistryProvider: ConfluentSchemaRegistryProvider = ConfluentSchemaRegistryProvider.avroPayload(confluentClientFactory)

  private lazy val confluentClient = schemaRegistryProvider.schemaRegistryClientFactory.create(kafkaConfig)

  test("all topic strategy test") {
    val strategy = new AllTopicsSelectionStrategy()
    strategy.getTopics(confluentClient).toList.map(_.toSet) shouldBe List(Set(RecordTopic, RecordTopicWithKey, IntTopicNoKey, IntTopicWithKey, InvalidDefaultsTopic))
  }

  test("topic filtering strategy test") {
    val strategy = new TopicPatternSelectionStrategy(Pattern.compile(".*Record.*"))
    strategy.getTopics(confluentClient).toList shouldBe List(List(RecordTopic, RecordTopicWithKey))
  }

  test("show how to override topic selection strategy") {
    new KafkaAvroSourceFactory(schemaRegistryProvider, testProcessObjectDependencies, None) {
      override def topicSelectionStrategy = new TopicPatternSelectionStrategy(Pattern.compile("test-.*"))
    }
  }

}
