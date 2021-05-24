package pl.touk.nussknacker.engine.avro.helpers

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.scalatest.{Assertion, Matchers}
import pl.touk.nussknacker.engine.avro.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{AbstractConfluentKafkaAvroDeserializer, AbstractConfluentKafkaAvroSerializer}
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, StringKeyedValue}
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KafkaZookeeperUtils}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets

trait KafkaWithSchemaRegistryOperations extends Matchers with PatientScalaFutures {

  import KafkaZookeeperUtils._

  def pushMessage(obj: Any, objectTopic: String, topic: Option[String] = None, timestamp: java.lang.Long = null): RecordMetadata = {
    val serializedObj = serialize(objectTopic, obj)
    kafkaClient.sendRawMessage(topic.getOrElse(objectTopic), Array.empty, serializedObj, None, timestamp).futureValue
  }

  protected def serialize(objectTopic: String, obj: Any): Array[Byte] = valueSerializer.serialize(objectTopic, obj)

  protected def valueSerializer: Serializer[Any] = new SimpleKafkaAvroSerializer(schemaRegistryClient)

  def pushMessage(kafkaSerializer: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]], obj: AnyRef, topic: String): RecordMetadata = {
    val record = kafkaSerializer.serialize(StringKeyedValue(null, obj), null)
    kafkaClient.sendRawMessage(topic, record.key(), record.value()).futureValue
  }

  def consumeAndVerifyMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, expected: List[Any]): Assertion = {
    val result = consumeMessages(kafkaDeserializer, topic, expected.length)
    result shouldBe expected
  }

  protected def consumeMessages(kafkaDeserializer: KafkaDeserializationSchema[_], topic: String, count: Int): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consumeWithConsumerRecord(topic).map { record =>
      kafkaDeserializer.deserialize(record)
    }.take(count).toList
  }

  def consumeAndVerifyMessage(topic: String, expected: Any, useSpecificAvroReader: Boolean = false): Assertion =
    consumeAndVerifyMessages(topic, List(expected), useSpecificAvroReader)

  protected def consumeAndVerifyMessages(topic: String, expected: List[Any], useSpecificAvroReader: Boolean = false): Assertion = {
    val result = consumeMessages(topic, expected.length, useSpecificAvroReader)
    result shouldBe expected
  }

  private def consumeMessages(topic: String, count: Int, useSpecificAvroReader: Boolean): List[Any] = {
    val consumer = kafkaClient.createConsumer()
    consumer.consume(topic).map { record =>
      deserialize(useSpecificAvroReader)(topic, record.message())
    }.take(count).toList
  }

  protected def deserialize(useSpecificAvroReader: Boolean)
                           (objectTopic: String, obj: Array[Byte]): Any = prepareValueDeserializer(useSpecificAvroReader).deserialize(objectTopic, obj)

  /**
   * Default Confluent Avro serialization components
   */
  protected def prepareValueDeserializer(useSpecificAvroReader: Boolean): Deserializer[Any] = new SimpleKafkaAvroDeserializer(schemaRegistryClient, useSpecificAvroReader)

  protected def schemaRegistryClient: CSchemaRegistryClient

  protected def kafkaClient: KafkaClient

  protected def kafkaTopicNamespace: String = getClass.getSimpleName

  protected def createAndRegisterTopicConfig(name: String, schema: Schema): TopicConfig =
    createAndRegisterTopicConfig(name, List(schema))

  /**
   * We should register difference input topic and output topic for each tests, because kafka topics are not cleaned up after test,
   * and we can have wrong results of tests..
   */
  protected def createAndRegisterTopicConfig(name: String, schemas: List[Schema]): TopicConfig = {
    val topicConfig = TopicConfig(name, schemas)

    schemas.foreach(schema => {
      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val outputSubject = ConfluentUtils.topicSubject(topicConfig.output, topicConfig.isKey)
      val parsedSchema = ConfluentUtils.convertToAvroSchema(schema)
      schemaRegistryClient.register(inputSubject, parsedSchema)
      schemaRegistryClient.register(outputSubject, parsedSchema)
    })

    topicConfig
  }

  case class TopicConfig(input: String, output: String, schemas: List[Schema], isKey: Boolean)

  object TopicConfig {
    private final val inputPrefix = "test.avro.input"
    private final val outputPrefix = "test.avro.output"

    def apply(input: String, output: String, schema: Schema, isKey: Boolean): TopicConfig =
      new TopicConfig(input, output, List(schema), isKey = isKey)

    def apply(testName: String, schemas: List[Schema]): TopicConfig = {
      val inputTopic = s"$inputPrefix.$kafkaTopicNamespace.$testName"
      val outputTopic = s"$outputPrefix.$kafkaTopicNamespace.$testName"
      new TopicConfig(inputTopic, outputTopic, schemas, isKey = false)
    }
  }

}


class SimpleKafkaAvroDeserializer(schemaRegistryClient: CSchemaRegistryClient, _useSpecificAvroReader: Boolean) extends AbstractConfluentKafkaAvroDeserializer with Deserializer[Any] {

  this.schemaRegistry = schemaRegistryClient
  this.useSpecificAvroReader = _useSpecificAvroReader

  override protected val schemaIdSerializationEnabled: Boolean = true

  def deserialize(topic: String, record: Array[Byte]): Any = {
    deserialize(topic, isKey = false, record, None)
  }
}

class SimpleKafkaAvroSerializer(schemaRegistryVal: CSchemaRegistryClient) extends AbstractConfluentKafkaAvroSerializer(new DefaultAvroSchemaEvolution) with Serializer[Any] {

  this.schemaRegistry = schemaRegistryVal

  override def serialize(topic: String, data: Any): Array[Byte] = serialize(None, topic, data, isKey = false)
}

object SimpleKafkaJsonDeserializer extends Deserializer[Any] {

  override def deserialize(topic: String, data: Array[Byte]): Any = {
    io.circe.parser.parse(new String(data, StandardCharsets.UTF_8)).right.get
  }
}

object SimpleKafkaJsonSerializer extends Serializer[Any] {

  val encoder: BestEffortJsonEncoder = BestEffortJsonEncoder(failOnUnkown = true)

  override def serialize(topic: String, data: Any): Array[Byte] = encoder.encode(data).spaces2.getBytes(StandardCharsets.UTF_8)
}