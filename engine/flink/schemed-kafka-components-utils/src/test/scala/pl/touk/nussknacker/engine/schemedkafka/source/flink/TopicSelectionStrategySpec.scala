package pl.touk.nussknacker.engine.schemedkafka.source.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.schemedkafka.{AllTopicsSelectionStrategy, TopicPatternSelectionStrategy}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory

import java.util.regex.Pattern

class TopicSelectionStrategySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val confluentClient = confluentClientFactory.create(kafkaConfig)

  test("all topic strategy test") {
    val strategy = new AllTopicsSelectionStrategy()
    strategy.getTopics(confluentClient).toList.map(_.toSet) shouldBe List(Set(RecordTopic, RecordTopicWithKey, IntTopicNoKey, IntTopicWithKey, InvalidDefaultsTopic, PaymentDateTopic))
  }

  test("topic filtering strategy test") {
    val strategy = new TopicPatternSelectionStrategy(Pattern.compile(".*Record.*"))
    strategy.getTopics(confluentClient).toList shouldBe List(List(RecordTopic, RecordTopicWithKey))
  }

  test("show how to override topic selection strategy") {
    new UniversalKafkaSourceFactory(confluentClientFactory, ConfluentSchemaBasedSerdeProvider.universal(confluentClientFactory), testProcessObjectDependencies, new FlinkKafkaSourceImplFactory(None)) {
      override def topicSelectionStrategy = new TopicPatternSelectionStrategy(Pattern.compile("test-.*"))
    }
  }

}
