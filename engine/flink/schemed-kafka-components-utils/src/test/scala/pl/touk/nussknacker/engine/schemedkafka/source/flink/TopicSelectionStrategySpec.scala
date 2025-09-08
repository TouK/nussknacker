package pl.touk.nussknacker.engine.schemedkafka.source.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.{
  TopicsMatchingPatternWithExistingSubjectsSelectionStrategy,
  TopicsWithExistingSubjectSelectionStrategy
}
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaSchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

import java.util.regex.Pattern

class TopicSelectionStrategySpec
    extends AnyFunSuite
    with KafkaSchemaRegistryMixin
    with WithMockSchemaRegistryWithSampleSchemasRegistered {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected lazy val kafkaComponentsConfigPrefix: String = "not-important"

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  private lazy val confluentClient = factory.create(kafkaComponentsConfig)

  test("all topic strategy test") {
    val strategy = new TopicsWithExistingSubjectSelectionStrategy(confluentClient)
    strategy.getTopics.toList.map(_.toSet) shouldBe List(
      Set(
        RecordTopic,
        RecordTopicWithKey,
        IntTopicNoKey,
        IntTopicWithKey,
        ArrayOfNumbersTopic,
        ArrayOfRecordsTopic,
        InvalidDefaultsTopic,
        PaymentDateTopic,
      )
    )
  }

  test("topic filtering strategy test") {
    val strategy =
      new TopicsMatchingPatternWithExistingSubjectsSelectionStrategy(Pattern.compile(".*Record.*"), confluentClient)
    strategy.getTopics.toList shouldBe List(
      List(ArrayOfRecordsTopic, RecordTopic, RecordTopicWithKey)
    )
  }

  test("show how to override topic selection strategy") {
    new UniversalKafkaSourceFactory(
      factory,
      UniversalSchemaBasedSerdeProvider.create(factory, kafkaComponentsConfig),
      kafkaComponentsConfig,
      NamingStrategy.Disabled,
      new FlinkKafkaSourceImplFactory
    ) {
      override lazy val topicSelectionStrategy =
        new TopicsMatchingPatternWithExistingSubjectsSelectionStrategy(Pattern.compile("test-.*"), confluentClient)
    }
  }

}
