package pl.touk.nussknacker.engine.kafka

import java.lang
import java.nio.charset.StandardCharsets

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{Deserializer, Serializer, StringDeserializer, StringSerializer}
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordDeserializationSchemaFactory, ConsumerRecordToJsonFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.BaseSimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.reflect.ClassTag

trait KafkaSourceFactoryMixin extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures {

  val sampleValue = SampleValue("first", "last")
  val sampleKey = SampleKey("one", 2L)
  val sampleHeaders = Map("headerOne" -> "valueOfHeaderOne", "headerTwo" -> null)

  val sampleTopic = "topic"
  val constTimestamp: Long = 123L
  lazy val kafkaConfig = KafkaConfig.parseConfig(config)

  protected def objToSerializeSerializationSchema(topic: String): KafkaSerializationSchema[Any] = new BaseSimpleSerializationSchema[ObjToSerialize](
    topic,
    obj => Option(obj.value).map(v => implicitly[Encoder[SampleValue]].apply(v).noSpaces).orNull,
    obj => Option(obj.key).map(k => implicitly[Encoder[SampleKey]].apply(k).noSpaces).orNull,
    obj => ConsumerRecordUtils.toHeaders(obj.headers)
  ).asInstanceOf[KafkaSerializationSchema[Any]]

  protected def createTopic(name: String, partitions: Int = 1): String = {
    kafkaClient.createTopic(name, partitions = partitions)
    name
  }

  protected def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: AnyRef, topic: String, partition: Option[Int] = None, timestamp: Long = 0L): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, timestamp)
    kafkaClient.sendRawMessage(topic = record.topic(), key = record.key(), content = record.value(), partition = partition, timestamp = record.timestamp(), headers = record.headers()).futureValue
  }

  protected def checkResult[K, V](a: ConsumerRecord[K, V], b: ConsumerRecord[K, V]): Assertion = {
    // "a shouldEqual b" raises TestFailedException so here compare field by field
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    a.offset() shouldEqual b.offset()
    a.timestamp() shouldEqual b.timestamp()
    a.timestampType() shouldEqual b.timestampType()
    // skipping checksum, deprecated and when event is read from topic it comes with calculated checksum
    a.serializedKeySize() shouldEqual b.serializedKeySize()
    a.serializedValueSize() shouldEqual b.serializedValueSize()
    a.key() shouldEqual b.key()
    a.value() shouldEqual b.value()
    a.headers() shouldEqual b.headers()
    a.leaderEpoch() shouldEqual b.leaderEpoch()
  }

  protected lazy val StringSourceFactory: KafkaSourceFactory[Any, Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, new StringDeserializer with Serializable)
    val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(new StringSerializer with Serializable, new StringSerializer with Serializable)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
      deserializationSchemaFactory.create(List(sampleTopic), kafkaConfig),
      serializationSchemaFactory.create(sampleTopic, kafkaConfig)
    )
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      None,
      testDataRecordFormatter,
      processObjectDependencies
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val SampleEventSourceFactory: KafkaSourceFactory[Any, Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, sampleValueJsonDeserializer)
    val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(new StringSerializer with Serializable, sampleValueJsonSerializer)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
      deserializationSchemaFactory.create(List(sampleTopic), kafkaConfig),
      serializationSchemaFactory.create(sampleTopic, kafkaConfig)
    )
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      None,
      testDataRecordFormatter,
      processObjectDependencies
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val ConsumerRecordValueSourceFactory: KafkaSourceFactory[Any, Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(new StringDeserializer with Serializable, sampleValueJsonDeserializer)
    val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(new StringSerializer with Serializable, sampleValueJsonSerializer)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
      deserializationSchemaFactory.create(List(sampleTopic), kafkaConfig),
      serializationSchemaFactory.create(sampleTopic, kafkaConfig)
    )
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      None,
      testDataRecordFormatter,
      processObjectDependencies
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val ConsumerRecordKeyValueSourceFactory: KafkaSourceFactory[Any, Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
    val serializationSchemaFactory = new SampleConsumerRecordSerializationSchemaFactory(sampleKeyJsonSerializer, sampleValueJsonSerializer)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(
      deserializationSchemaFactory.create(List(sampleTopic), kafkaConfig),
      serializationSchemaFactory.create(sampleTopic, kafkaConfig)
    )
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      None,
      testDataRecordFormatter,
      processObjectDependencies
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

}

object KafkaSourceFactoryMixin {

  @JsonCodec case class SampleKey(partOne: String, partTwo: Long)
  @JsonCodec case class SampleValue(id: String, field: String)
  @JsonCodec case class ObjToSerialize(value: SampleValue, key: SampleKey, headers: Map[String, String])

  implicit val sampleKeyTypeInformation: TypeInformation[SampleKey] = TypeInformation.of(classOf[SampleKey])
  implicit val sampleValueTypeInformation: TypeInformation[SampleValue] = TypeInformation.of(classOf[SampleValue])
  implicit val stringTypeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])

  val sampleKeyJsonDeserializer: Deserializer[SampleKey] = createDeserializer[SampleKey]
  val sampleValueJsonDeserializer: Deserializer[SampleValue] = createDeserializer[SampleValue]
  val sampleKeyJsonSerializer: Serializer[SampleKey] = createSerializer[SampleKey]
  val sampleValueJsonSerializer: Serializer[SampleValue] = createSerializer[SampleValue]

  def createDeserializer[T: Decoder]: Deserializer[T] = new Deserializer[T] with Serializable {
    override def deserialize(topic: String, data: Array[Byte]): T = decodeJsonUnsafe[T](data)
  }
  def createSerializer[T: Encoder]: Serializer[T] = new Serializer[T] {
    override def serialize(topic: String, data: T): Array[Byte] = Encoder[T].apply(data).noSpaces.getBytes(StandardCharsets.UTF_8)
  }

}

class SampleConsumerRecordDeserializationSchemaFactory[K: ClassTag, V: ClassTag](keyDeserializer: Deserializer[K], valueDeserializer: Deserializer[V])
  extends ConsumerRecordDeserializationSchemaFactory[K, V] {
  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K] = keyDeserializer
  override protected def createValueDeserializer(kafkaConfig: KafkaConfig): Deserializer[V] = valueDeserializer
}

class SampleConsumerRecordSerializationSchemaFactory[K, V](keySerializer: Serializer[K], valueSerializer: Serializer[V])
  extends KafkaSerializationSchemaFactory[ConsumerRecord[K, V]] {
  override def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[ConsumerRecord[K, V]] = {
    new KafkaSerializationSchema[ConsumerRecord[K, V]] {
      override def serialize(element: ConsumerRecord[K, V], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        new ProducerRecord[Array[Byte], Array[Byte]](
          element.topic(),
          element.partition(),
          keySerializer.serialize(topic, element.key()),
          valueSerializer.serialize(topic, element.value()),
          element.headers()
        )
      }
    }
  }
}