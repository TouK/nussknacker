package pl.touk.nussknacker.engine.kafka

import io.circe.Encoder
import io.circe.generic.JsonCodec
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.{ConsumerRecordDeserializationSchemaFactory, ConsumerRecordToJsonFormatter, DeserializedConsumerRecord}
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaDeserializationSchemaFactory, KafkaDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{BaseSimpleSerializationSchema, JsonSerializationSchema, SimpleSerializationSchema}
import pl.touk.nussknacker.engine.kafka.source.KafkaGenericNodeSourceFactory
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.reflect.classTag

class KafkaGenericNodeSourceFactorySpec extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures {

  @JsonCodec case class SampleEvent(first: String, middle: String, last: String)

  implicit val sampleEventTypeInformation: TypeInformation[SampleEvent] = TypeInformation.of(classTag[SampleEvent].runtimeClass.asInstanceOf[Class[SampleEvent]])

  @JsonCodec case class SampleKey(partOne: String, partTwo: String)

  implicit val sampleKeyTypeInformation: TypeInformation[SampleKey] = TypeInformation.of(classTag[SampleKey].runtimeClass.asInstanceOf[Class[SampleKey]])

  @JsonCodec case class ObjToSerialize(value: SampleEvent, key: SampleKey, headers: Map[String, Option[String]])

  private lazy val ObjToSerializeSerializationSchema = new BaseSimpleSerializationSchema[ObjToSerialize](
    Topic,
    obj => implicitly[Encoder[SampleEvent]].apply(obj.value).noSpaces,
    obj => implicitly[Encoder[SampleKey]].apply(obj.key).noSpaces,
    obj => obj.headers
  ).asInstanceOf[KafkaSerializationSchema[Any]]

  private lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  private lazy val nodeId: NodeId = NodeId("mock-node-id")

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
    val schema = new EspDeserializationSchema[SampleEvent](bytes => decodeJsonUnsafe[SampleEvent](bytes))
    val deserializationSchemaFactory: KafkaDeserializationSchemaFactory[SampleEvent] = FixedKafkaDeserializationSchemaFactory(schema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, BasicFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  protected lazy val ConsumerRecordValueSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val schema = ConsumerRecordDeserializationSchemaFactory.create(bytes => decodeJsonUnsafe[SampleEvent](bytes))
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(schema)
    val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  protected lazy val ConsumerRecordKeyValueSourceFactory: KafkaGenericNodeSourceFactory[Any] = {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val schema = ConsumerRecordDeserializationSchemaFactory.create(bytes => decodeJsonUnsafe[SampleKey](bytes), bytes => decodeJsonUnsafe[SampleEvent](bytes))
    val testDataRecordFormatter = new ConsumerRecordToJsonFormatter(schema)
    val deserializationSchemaFactory = new FixedKafkaDeserializationSchemaFactory(schema)
    val sourceFactory = new KafkaGenericNodeSourceFactory(deserializationSchemaFactory, None, testDataRecordFormatter, processObjectDependencies, None)
    sourceFactory.asInstanceOf[KafkaGenericNodeSourceFactory[Any]]
  }

  def pushMessage(kafkaSerializer: KafkaSerializationSchema[Any], obj: AnyRef, partition: Option[Int] = None): RecordMetadata = {
    val record: ProducerRecord[Array[Byte], Array[Byte]] = kafkaSerializer.serialize(obj, null)
    kafkaClient.sendRawMessage(topic = record.topic(), key = record.key(), content = record.value(), partition = partition, timestamp = 0L, headers = record.headers()).futureValue
  }

  private def readLastMessage(sourceFactory: KafkaGenericNodeSourceFactory[Any], topic: String, numberOfMessages: Int = 1): Any = {
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
    val givenObj = SampleEvent("first", "middle", "last")
    pushMessage(new JsonSerializationSchema[SampleEvent](Topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj)
    val result = readLastMessage(SampleEventSourceFactory, Topic)
    result shouldEqual List(givenObj)
  }

  test("read and deserialize consumer record with value only") {
    val givenObj = SampleEvent("first", "middle", "last")
    val expectedObj = DeserializedConsumerRecord[String, SampleEvent](givenObj, None, Topic,0, 0, 0L, Map())
    pushMessage(new JsonSerializationSchema[SampleEvent](Topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj)
    val result = readLastMessage(ConsumerRecordValueSourceFactory, Topic)
    result shouldEqual List(expectedObj)
  }

  test("read and deserialize consumer record with key, value and headers") {
    val givenObj = SampleEvent("first", "middle", "last")
    val givenKey = SampleKey("one", "two")
    val givenHeaders = Map("headerOne" -> Some("valueOfHeaderOne"), "headerTwo" -> None)
    val expectedObj = DeserializedConsumerRecord[SampleKey, SampleEvent](givenObj, Some(givenKey), Topic, 0, 0, 0L, givenHeaders)
    pushMessage(ObjToSerializeSerializationSchema, ObjToSerialize(givenObj, givenKey, givenHeaders))
    val result = readLastMessage(ConsumerRecordKeyValueSourceFactory, Topic)
    result shouldEqual List(expectedObj)
  }

  test("read and deserialize consumer record with value only, multiple partitions and offsets") {
    val givenObj = List(
      SampleEvent("first0", "middle0", "last0"),
      SampleEvent("first1", "middle1", "last1"),
      SampleEvent("first2", "middle2", "last2"),
      SampleEvent("first3", "middle3", "last3")
    )
    val serializationSchema = new JsonSerializationSchema[SampleEvent](TopicWithPartitions).asInstanceOf[KafkaSerializationSchema[Any]]

    pushMessage(serializationSchema, givenObj(0), partition = Some(0))
    pushMessage(serializationSchema, givenObj(1), partition = Some(0))
    pushMessage(serializationSchema, givenObj(2), partition = Some(1))
    pushMessage(serializationSchema, givenObj(3), partition = Some(1))

    val result = readLastMessage(ConsumerRecordValueSourceFactory, TopicWithPartitions, 4)
    val valuePartitionOffsetToCheck = result.asInstanceOf[List[DeserializedConsumerRecord[SampleKey, SampleEvent]]]
      .map(record => (record.value, record.partition, record.offset))
      .toSet

    valuePartitionOffsetToCheck shouldBe Set(
      (SampleEvent("first0", "middle0", "last0"), 0, 0),
      (SampleEvent("first1", "middle1", "last1"), 0, 1),
      (SampleEvent("first2", "middle2", "last2"), 1, 2),
      (SampleEvent("first3", "middle3", "last3"), 1, 3)
    )
  }

  protected override def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(Topic, partitions = 1)
    kafkaClient.createTopic(TopicWithPartitions, partitions = 2)
  }
}

