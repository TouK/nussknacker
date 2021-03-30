package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.serialization.{FixedKafkaDeserializationSchemaFactory, KafkaDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{JsonSerializationSchema, SimpleSerializationSchema}
import pl.touk.nussknacker.engine.kafka.source.KafkaGenericNodeSourceFactory
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin._
import pl.touk.nussknacker.engine.kafka.util.{ConsumerRecordToJsonFormatter, KafkaGenericNodeMixin}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.PatientScalaFutures


class KafkaGenericNodeSourceFactorySpec extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures with KafkaGenericNodeMixin {

  private lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  private lazy val nodeId: NodeId = NodeId("mock-node-id")

  private val sampleValue = SampleValue("first", "last")
  private val sampleKey = SampleKey("one", 2L)
  private val sampleHeaders = Map("headerOne" -> "valueOfHeaderOne", "headerTwo" -> null)
  private val constTimestamp: Long = 123L

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
    val topic = createTopic("simpleString")
    val givenObj = "sample text"
    pushMessage(new SimpleSerializationSchema[Any](topic, String.valueOf), givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(StringSourceFactory, topic)
    result shouldEqual List(givenObj)
  }

  test("read and deserialize from simple json source") {
    val topic = createTopic("simpleJson")
    val givenObj = sampleValue
    pushMessage(new JsonSerializationSchema[SampleValue](topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(SampleEventSourceFactory, topic)
    result shouldEqual List(givenObj)
  }

  test("read and deserialize consumer record with value only") {
    val topic = createTopic("consumerRecordNoKey")
    val givenObj = sampleValue
    val expectedObj = new ConsumerRecord[String, SampleValue](topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, 0L, -1, 29, null, givenObj, ConsumerRecordUtils.emptyHeaders)
    pushMessage(new JsonSerializationSchema[SampleValue](topic).asInstanceOf[KafkaSerializationSchema[Any]], givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(ConsumerRecordValueSourceFactory, topic).head.asInstanceOf[ConsumerRecord[String, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with key, value and headers") {
    val topic = createTopic("consumerRecordKeyValueHeaders")
    val givenObj = ObjToSerialize(sampleValue, sampleKey, sampleHeaders)
    val expectedObj = new ConsumerRecord[SampleKey, SampleValue](topic, 0, 0L, constTimestamp, TimestampType.CREATE_TIME, 0L, 29, 29, sampleKey, sampleValue, ConsumerRecordUtils.toHeaders(sampleHeaders))
    pushMessage(objToSerializeSerializationSchema(topic), givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(ConsumerRecordKeyValueSourceFactory, topic).head.asInstanceOf[ConsumerRecord[SampleKey, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with value only, multiple partitions and offsets") {
    val topic = createTopic("consumerRecordNoKeyTwoPartitions", 2)
    val givenObj = List(
      SampleValue("first0", "last0"),
      SampleValue("first1", "last1"),
      SampleValue("first2", "last2"),
      SampleValue("first3", "last3")
    )
    val serializationSchema = new JsonSerializationSchema[SampleValue](topic).asInstanceOf[KafkaSerializationSchema[Any]]

    pushMessage(serializationSchema, givenObj(0), topic, partition = Some(0), timestamp = constTimestamp)
    pushMessage(serializationSchema, givenObj(1), topic, partition = Some(0), timestamp = constTimestamp)
    pushMessage(serializationSchema, givenObj(2), topic, partition = Some(1), timestamp = constTimestamp)
    pushMessage(serializationSchema, givenObj(3), topic, partition = Some(1), timestamp = constTimestamp)

    val result = readLastMessage(ConsumerRecordValueSourceFactory, topic, 4)
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

  private def checkResult[K, V](a: ConsumerRecord[K, V], b: ConsumerRecord[K, V]) = {
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
}

