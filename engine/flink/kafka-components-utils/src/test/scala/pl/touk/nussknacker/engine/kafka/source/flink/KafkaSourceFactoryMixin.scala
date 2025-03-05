package pl.touk.nussknacker.engine.kafka.source.flink

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaConfig, KafkaRecordUtils, KafkaSpec}
import pl.touk.nussknacker.engine.kafka.consumerrecord.{
  ConsumerRecordDeserializationSchemaFactory,
  ConsumerRecordToJsonFormatterFactory
}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.BaseSimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import java.util.Optional
import scala.reflect.ClassTag

trait KafkaSourceFactoryMixin extends AnyFunSuite with Matchers with KafkaSpec with PatientScalaFutures {

  val sampleValue: SampleValue              = SampleValue("first", "last")
  val sampleKey: SampleKey                  = SampleKey("one", 2L)
  val sampleHeadersMap: Map[String, String] = Map("headerOne" -> "valueOfHeaderOne", "headerTwo" -> null)
  val sampleHeaders: Headers                = KafkaRecordUtils.toHeaders(sampleHeadersMap)

  val sampleTopic                   = "topic"
  val constTimestamp: Long          = 123L
  lazy val kafkaConfig: KafkaConfig = KafkaConfig.parseConfig(config)

  lazy val modelDependencies: ProcessObjectDependencies = ProcessObjectDependencies.withConfig(config)

  protected def objToSerializeSerializationSchema(topic: String): serialization.KafkaSerializationSchema[Any] =
    new BaseSimpleSerializationSchema[ObjToSerialize](
      topic,
      (obj: ObjToSerialize) => Option(obj.value).map(v => implicitly[Encoder[SampleValue]].apply(v).noSpaces).orNull,
      (obj: ObjToSerialize) => Option(obj.key).map(k => implicitly[Encoder[SampleKey]].apply(k).noSpaces).orNull,
      (obj: ObjToSerialize) => KafkaRecordUtils.toHeaders(obj.headers)
    ).asInstanceOf[serialization.KafkaSerializationSchema[Any]]

  protected def createTopic(name: String, partitions: Int = 1): String = {
    kafkaClient.createTopic(name, partitions = partitions)
    name
  }

  protected def pushMessage(
      kafkaSerializer: serialization.KafkaSerializationSchema[Any],
      obj: AnyRef,
      partition: Option[Int] = None,
      timestamp: Long = 0L
  ): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, timestamp)
    kafkaClient
      .sendRawMessage(
        topic = record.topic(),
        key = record.key(),
        content = record.value(),
        partition = partition,
        timestamp = record.timestamp(),
        headers = record.headers()
      )
      .futureValue
  }

  protected def checkResult[K, V](a: ConsumerRecord[K, V], b: ConsumerRecord[K, V]): Assertion = {
    // "a shouldEqual b" raises TestFailedException so here compare field by field
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    a.offset() shouldEqual b.offset()
    a.timestamp() shouldEqual b.timestamp()
    a.timestampType() shouldEqual b.timestampType()
    // skipping checksum, deprecated and when event is read from topic it comes with calculated checksum
    // skipping serializedKeySize and serializedValueSize
    a.key() shouldEqual b.key()
    a.value() shouldEqual b.value()
    a.headers() shouldEqual b.headers()
    a.leaderEpoch() shouldEqual b.leaderEpoch()
  }

  protected lazy val StringSourceFactory: KafkaSourceFactory[Any, Any] = {
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
      new StringDeserializer with Serializable,
      new StringDeserializer with Serializable
    )
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, String]
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      formatterFactory,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val SampleEventSourceFactory: KafkaSourceFactory[Any, Any] = {
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
      new StringDeserializer with Serializable,
      sampleValueJsonDeserializer
    )
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, SampleValue]
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      formatterFactory,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val ConsumerRecordValueSourceFactory: KafkaSourceFactory[Any, Any] = {
    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(
      new StringDeserializer with Serializable,
      sampleValueJsonDeserializer
    )
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[String, SampleValue]
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      formatterFactory,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

  protected lazy val ConsumerRecordKeyValueSourceFactory: KafkaSourceFactory[Any, Any] = {
    val deserializationSchemaFactory =
      new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
    val formatterFactory = new ConsumerRecordToJsonFormatterFactory[SampleKey, SampleValue]
    val sourceFactory = new KafkaSourceFactory(
      deserializationSchemaFactory,
      formatterFactory,
      modelDependencies,
      new FlinkKafkaSourceImplFactory(None)
    )
    sourceFactory.asInstanceOf[KafkaSourceFactory[Any, Any]]
  }

}

object KafkaSourceFactoryMixin {

  @JsonCodec case class SampleKey(partOne: String, partTwo: Long) extends DisplayJsonWithEncoder[SampleKey]
  @JsonCodec case class SampleValue(id: String, field: String)    extends DisplayJsonWithEncoder[SampleValue]
  @JsonCodec case class ObjToSerialize(value: SampleValue, key: SampleKey, headers: Map[String, String])

  val sampleKeyJsonDeserializer: Deserializer[SampleKey]     = createDeserializer[SampleKey]
  val sampleValueJsonDeserializer: Deserializer[SampleValue] = createDeserializer[SampleValue]

  def createDeserializer[T: Decoder]: Deserializer[T] = new Deserializer[T] with Serializable {
    override def deserialize(topic: String, data: Array[Byte]): T = decodeJsonUnsafe[T](data)
  }

  def createConsumerRecord[Key, Value](
      topic: String,
      partition: Int,
      offset: Long,
      timestamp: Long,
      timestampType: TimestampType,
      key: Key,
      value: Value,
      headers: Headers,
      leaderEpoch: Optional[Integer]
  ): ConsumerRecord[Key, Value] = {
    new ConsumerRecord(
      topic,
      partition,
      offset,
      timestamp,
      timestampType,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      key,
      value,
      headers,
      leaderEpoch
    )
  }

  def serialize[T: Encoder](data: T): Array[Byte] = Encoder[T].apply(data).noSpaces.getBytes(StandardCharsets.UTF_8)

  def serializeKeyValue(key: Option[String], value: String): (Array[Byte], Array[Byte]) =
    (key.map(_.getBytes(StandardCharsets.UTF_8)).orNull, value.getBytes(StandardCharsets.UTF_8))
  def serializeKeyValue[V: Encoder](key: Option[String], value: V): (Array[Byte], Array[Byte]) =
    (key.map(_.getBytes(StandardCharsets.UTF_8)).orNull, serialize[V](value))
  def serializeKeyValue[K: Encoder, V: Encoder](key: Option[K], value: V): (Array[Byte], Array[Byte]) =
    (key.map(serialize[K]).orNull, serialize[V](value))

}

class SampleConsumerRecordDeserializationSchemaFactory[K: ClassTag, V: ClassTag](
    keyDeserializer: Deserializer[K],
    valueDeserializer: Deserializer[V]
) extends ConsumerRecordDeserializationSchemaFactory[K, V] {
  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K]   = keyDeserializer
  override protected def createValueDeserializer(kafkaConfig: KafkaConfig): Deserializer[V] = valueDeserializer
}
