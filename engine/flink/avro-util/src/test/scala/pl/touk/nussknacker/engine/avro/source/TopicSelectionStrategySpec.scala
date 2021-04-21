package pl.touk.nussknacker.engine.avro.source

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AllTopicsSelectionStrategy, TopicPatternSelectionStrategy}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory

import java.util.regex.Pattern

class TopicSelectionStrategySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val confluentClient = schemaRegistryProvider.createSchemaRegistryClient

  test("all topic strategy test") {
    val strategy = new AllTopicsSelectionStrategy()
    strategy.getTopics(confluentClient).toList.map(_.toSet) shouldBe List(Set(RecordTopic, IntTopic, InvalidDefaultsTopic))
  }

  test("topic filtering strategy test") {
    val strategy = new TopicPatternSelectionStrategy(Pattern.compile(".*Record.*"))
    strategy.getTopics(confluentClient).toList shouldBe List(List(RecordTopic))
  }

}
