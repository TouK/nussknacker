package pl.touk.nussknacker.engine.kafka

import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordDeserializationSchemaFactory, ConsumerRecordToJsonFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaDeserializationSchemaFactory, KafkaDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{BaseSimpleSerializationSchema, JsonSerializationSchema, SimpleSerializationSchema}
import pl.touk.nussknacker.engine.kafka.source.KafkaGenericNodeSourceFactory
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.reflect.classTag

class KafkaGenericNodeSourceFactorySpec extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures {

  @JsonCodec case class SampleKey(partOne: String, partTwo: String)

  implicit val keyTypeInformation: TypeInformation[SampleKey] = TypeInformation.of(classTag[SampleKey].runtimeClass.asInstanceOf[Class[SampleKey]])

  val keyDeserializationSchema = new EspDeserializationSchema[SampleKey](bytes => decodeJsonUnsafe[SampleKey](bytes))

  @JsonCodec case class SampleValue(first: String, middle: String, last: String)

  implicit val valueTypeInformation: TypeInformation[SampleValue] = TypeInformation.of(classTag[SampleValue].runtimeClass.asInstanceOf[Class[SampleValue]])

  val valueDeserializationSchema = new EspDeserializationSchema[SampleValue](bytes => decodeJsonUnsafe[SampleValue](bytes))

  @JsonCodec case class ObjToSerialize(value: SampleValue, key: SampleKey, headers: Map[String, Option[String]])

  private lazy val ObjToSerializeSerializationSchema = new BaseSimpleSerializationSchema[ObjToSerialize](
    Topic,
    obj => implicitly[Encoder[SampleValue]].apply(obj.value).noSpaces,
    obj => implicitly[Encoder[SampleKey]].apply(obj.key).noSpaces,
    obj => ConsumerRecordUtils.toHeaders(obj.headers)
  ).asInstanceOf[KafkaSerializationSchema[Any]]

  private lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  private lazy val nodeId: NodeId = NodeId("mock-node-id")

  private val constTimestamp: Long = 123L

  private val Topic: String = "testKafkaGenericNodeTopic"
  private val TopicWithPartitions: String = "testKafkaGenericNodeTopicWithPartitions"

  protected lazy val StringSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory: KafkaDeserializationSchemaFactory[String] = FixedKafkaDeserializationSchemaFactory(new SimpleStringSchema().asInstanceOf[DeserializationSchema[String]])
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, BasicFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  protected lazy val SampleEventSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val deserializationSchemaFactory: KafkaDeserializationSchemaFactory[SampleValue] = FixedKafkaDeserializationSchemaFactory(valueDeserializationSchema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, BasicFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  protected lazy val ConsumerRecordValueSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val schema = ConsumerRecordDeserializationSchemaFactory.create(valueDeserializationSchema)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter
    val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  protected lazy val ConsumerRecordKeyValueSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val schema = ConsumerRecordDeserializationSchemaFactory.create(keyDeserializationSchema, valueDeserializationSchema)
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter
    val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: AnyRef, partition: Option[Int] = None): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, constTimestamp)
    kafkaClient.sendRawMessage(topic = record.topic(), key = record.key(), content = record.value(), partition = partition, timestamp = record.timestamp(), headers = record.headers()).futureValue
  }

  private def readLastMessage(sourceFactory: KafkaGenericNodeSourceFactory[Any], topic: String, numberOfMessages: Int = 1): List[AnyRef] = {
    val source = createSource(sourceFactory, topic)
    val bytes = source.generateTestData(numberOfMessages)
    source.testDataParser.parseTestData(bytes)
  }

  private def createSource(sourceFactory: KafkaGenericNodeSourceFactory[Any], topic: String): Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef] with ReturningType = {
    val source = sourceFactory
      .implementation(Map(KafkaGenericNodeSourceFactory.TopicParamName -> topic),
        List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)), None)
      .asInstanceOf[Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef] with ReturningType]
    source
  }

  test("read and deserialize from simple string source") {
    val givenObj = "sample text"
    pushMessage(new SimpleSerializationSchema[Any](Topic, String.valueOf), givenObj)
    val result = readLastMessage(StringSourceFactory, Topic)
    result shouldEqual List(givenObj)
  }

  test("read and deserialize from simple json source") {
    val givenObj = SampleValue("first", "middle", "last")
    pushMessage(new JsonSerializationSchema[SampleValue](Topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj)
    val result = readLastMessage(SampleEventSourceFactory, Topic)
    result shouldEqual List(givenObj)
  }

  test("read and deserialize consumer record with value only") {
    val givenObj = SampleValue("first", "middle", "last")
    val expectedObj = new ConsumerRecord[String, SampleValue](Topic, 0, 2L, constTimestamp, TimestampType.CREATE_TIME, 0L, -1, 49, null, givenObj, ConsumerRecordUtils.emptyHeaders)
    pushMessage(new JsonSerializationSchema[SampleValue](Topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj)
    val result = readLastMessage(ConsumerRecordValueSourceFactory, Topic).head.asInstanceOf[ConsumerRecord[String, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with key, value and headers") {
    val givenObj = SampleValue("first", "middle", "last")
    val givenKey = SampleKey("one", "two")
    val givenHeaders = Map("headerOne" -> Some("valueOfHeaderOne"), "headerTwo" -> None)
    val expectedObj = new ConsumerRecord[SampleKey, SampleValue](Topic, 0, 3L, constTimestamp, TimestampType.CREATE_TIME, 0L, 33, 49, givenKey, givenObj, ConsumerRecordUtils.toHeaders(givenHeaders))
    pushMessage(ObjToSerializeSerializationSchema, ObjToSerialize(givenObj, givenKey, givenHeaders))
    val result = readLastMessage(ConsumerRecordKeyValueSourceFactory, Topic).head.asInstanceOf[ConsumerRecord[SampleKey, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with value only, multiple partitions and offsets") {
    val givenObj = List(
      SampleValue("first0", "middle0", "last0"),
      SampleValue("first1", "middle1", "last1"),
      SampleValue("first2", "middle2", "last2"),
      SampleValue("first3", "middle3", "last3")
    )
    val serializationSchema = new JsonSerializationSchema[SampleValue](TopicWithPartitions).asInstanceOf[KafkaSerializationSchema[Any]]

    pushMessage(serializationSchema, givenObj(0), partition = Some(0))
    pushMessage(serializationSchema, givenObj(1), partition = Some(0))
    pushMessage(serializationSchema, givenObj(2), partition = Some(1))
    pushMessage(serializationSchema, givenObj(3), partition = Some(1))

    val result = readLastMessage(ConsumerRecordValueSourceFactory, TopicWithPartitions, 4)
    val valuePartitionOffsetToCheck = result.asInstanceOf[List[ConsumerRecord[SampleKey, SampleValue]]]
      .map(record => (record.value, record.partition, record.offset))
      .toSet

    valuePartitionOffsetToCheck shouldBe Set(
      (givenObj(0), 0, 0),
      (givenObj(1), 0, 1),
      (givenObj(2), 1, 0),
      (givenObj(3), 1, 1)
    )
  }

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(Topic, partitions = 1)
    kafkaClient.createTopic(TopicWithPartitions, partitions = 2)
  }

  private def checkResult[K, V](a: ConsumerRecord[K, V], b: ConsumerRecord[K, V]) = {
    a.topic() shouldEqual b.topic()
    a.partition() shouldEqual b.partition()
    // skipping offset, depends on the order of test execution
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
}

