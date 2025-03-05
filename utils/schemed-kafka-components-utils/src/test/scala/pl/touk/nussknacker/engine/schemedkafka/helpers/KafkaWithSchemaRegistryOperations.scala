package pl.touk.nussknacker.engine.schemedkafka.helpers

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaClient, KafkaRecordUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.AbstractConfluentKafkaAvroSerializer
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.nio.charset.StandardCharsets

trait KafkaWithSchemaRegistryOperations extends Matchers with ScalaFutures with Eventually {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))

  def pushMessage(
      obj: Any,
      topicToSerialize: TopicName.ForSource,
      topicToSend: Option[TopicName.ForSource] = None,
      timestamp: java.lang.Long = null,
      headers: Headers = KafkaRecordUtils.emptyHeaders
  ): RecordMetadata = {
    val serializedObj = valueSerializer.serialize(topicToSerialize.name, obj)
    kafkaClient
      .sendRawMessage(topicToSend.getOrElse(topicToSerialize).name, null, serializedObj, None, timestamp, headers)
      .futureValue
  }

  def pushMessageWithKey(
      key: Any,
      value: Any,
      topicToSerialize: String,
      topicToSend: Option[String] = None,
      timestamp: java.lang.Long = null,
      useStringForKey: Boolean = false
  ): RecordMetadata = {
    val serializedKey = if (useStringForKey) {
      key match {
        case str: String => str.getBytes(StandardCharsets.UTF_8)
        case null        => null
        case _           => throw new IllegalArgumentException("Expected string or null")
      }
    } else {
      keySerializer.serialize(topicToSerialize, key)
    }
    val serializedValue = valueSerializer.serialize(topicToSerialize, value)
    kafkaClient
      .sendRawMessage(topicToSend.getOrElse(topicToSerialize), serializedKey, serializedValue, None, timestamp)
      .futureValue
  }

  protected def keySerializer: Serializer[Any] = new SimpleKafkaAvroSerializer(schemaRegistryClient, isKey = true)

  protected def valueSerializer: Serializer[Any] = new SimpleKafkaAvroSerializer(schemaRegistryClient, isKey = false)

  def consumeAndVerifyMessages(
      kafkaDeserializer: serialization.KafkaDeserializationSchema[_],
      topic: TopicName.ForSink,
      expected: List[Any]
  ): Assertion = {
    val result =
      consumeMessages(kafkaDeserializer, topic, expected.length).map(
        _.asInstanceOf[ConsumerRecord[Any, Any]].value()
      )
    result shouldBe expected
  }

  protected def consumeMessages(
      kafkaDeserializer: serialization.KafkaDeserializationSchema[_],
      topic: TopicName.ForSink,
      count: Int
  ): List[Any] =
    kafkaClient
      .createConsumer()
      .consumeWithConsumerRecord(topic.name)
      .take(count)
      .map { record =>
        kafkaDeserializer.deserialize(record)
      }
      .toList

  def consumeAndVerifyMessage(topic: TopicName.ForSink, expected: Any): Assertion =
    consumeAndVerifyMessages(topic, List(expected))

  protected def consumeAndVerifyMessages(
      topic: TopicName.ForSink,
      expected: List[Any]
  ): Assertion = {
    val result = consumeMessages(topic, expected.length)
    result shouldBe expected
  }

  private def consumeMessages(topic: TopicName.ForSink, count: Int): List[Any] =
    kafkaClient
      .createConsumer()
      .consumeWithConsumerRecord(topic.name)
      .take(count)
      .map { record =>
        deserialize(topic, record.value())
      }
      .toList

  protected def deserialize(objectTopic: TopicName.ForSink, obj: Array[Byte]): Any =
    prepareValueDeserializer.deserialize(objectTopic.name, obj)

  /**
   * Default Confluent Avro serialization components
   */
  protected def prepareValueDeserializer: Deserializer[Any] =
    new SimpleKafkaAvroDeserializer(schemaRegistryClient)

  protected def schemaRegistryClient: CSchemaRegistryClient

  protected def kafkaClient: KafkaClient

  protected def kafkaTopicNamespace: String = getClass.getSimpleName

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  /**
   * We should register different input topic and output topic for each tests, because kafka topics are not cleaned up after test,
   * and we can have wrong results of tests.
   */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      registerSchema(topicConfig.input.toUnspecialized, schema, topicConfig.isKey)
      registerSchema(topicConfig.output.toUnspecialized, schema, topicConfig.isKey)
    })

    topicConfig
  }

  protected def registerSchema(name: UnspecializedTopicName, schema: Schema, isKey: Boolean): Int = {
    val subject      = ConfluentUtils.topicSubject(name, isKey)
    val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
    schemaRegistryClient.register(subject, parsedSchema)
  }

  protected def registerJsonSchema(name: UnspecializedTopicName, schema: String, isKey: Boolean): Int = {
    val subject      = ConfluentUtils.topicSubject(name, isKey)
    val parsedSchema = new JsonSchema(schema)
    schemaRegistryClient.register(subject, parsedSchema)
  }

  case class TopicConfig(input: TopicName.ForSource, output: TopicName.ForSink, schemas: List[Schema], isKey: Boolean)

  object TopicConfig {
    private final val inputPrefix  = "test.avro.input"
    private final val outputPrefix = "test.avro.output"

    def apply(input: TopicName.ForSource, output: TopicName.ForSink, schema: Schema, isKey: Boolean): TopicConfig =
      new TopicConfig(input, output, List(schema), isKey = isKey)

    def apply(testName: String, schemas: List[Schema]): TopicConfig = {
      val inputTopic  = TopicName.ForSource(s"$inputPrefix.$kafkaTopicNamespace.$testName")
      val outputTopic = TopicName.ForSink(s"$outputPrefix.$kafkaTopicNamespace.$testName")
      new TopicConfig(inputTopic, outputTopic, schemas, isKey = false)
    }

  }

}

class SimpleKafkaAvroSerializer(schemaRegistryVal: CSchemaRegistryClient, isKey: Boolean)
    extends AbstractConfluentKafkaAvroSerializer(new DefaultAvroSchemaEvolution)
    with Serializer[Any] {

  this.schemaRegistry = schemaRegistryVal

  override def serialize(topic: String, data: Any): Array[Byte] = serialize(topic, null, data)

  override def serialize(topic: String, headers: Headers, data: Any): Array[Byte] =
    serialize(None, topic, data, isKey, headers)

  // It is a work-around for two different close() methods (one throws IOException and another not) in AbstractKafkaSchemaSerDe and in Serializer
  // It is needed only for scala 2.12
  override def close(): Unit = {}

}

object SimpleKafkaJsonDeserializer extends Deserializer[Any] {

  override def deserialize(topic: String, data: Array[Byte]): Any = {
    io.circe.parser.parse(new String(data, StandardCharsets.UTF_8)).toOption.get
  }

}

object SimpleKafkaJsonSerializer extends Serializer[Any] {

  override def serialize(topic: String, data: Any): Array[Byte] =
    ToJsonEncoder.defaultForTests.encode(data).spaces2.getBytes(StandardCharsets.UTF_8)
}
