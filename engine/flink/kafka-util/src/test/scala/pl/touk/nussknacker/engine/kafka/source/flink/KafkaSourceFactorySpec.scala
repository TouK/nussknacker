package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedSingleParameter, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{JsonSerializationSchema, SimpleSerializationSchema}
import KafkaSourceFactory.KafkaSourceFactoryState
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, KafkaSpec, serialization}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.Optional


class KafkaSourceFactorySpec extends FunSuite with Matchers with KafkaSpec with PatientScalaFutures with KafkaSourceFactoryMixin {

  private lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  private lazy val nodeId: NodeId = NodeId("mock-node-id")

  private def readLastMessage(sourceFactory: KafkaSourceFactory[Any, Any], topic: String, numberOfMessages: Int = 1): List[AnyRef] = {
    val source = createSource(sourceFactory, topic)
    val bytes = source.generateTestData(numberOfMessages)
    source.testDataParser.parseTestData(TestData(bytes, numberOfMessages))
  }

  private def createSource(sourceFactory: KafkaSourceFactory[Any, Any], topic: String): Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef] with ReturningType = {
    val finalState = KafkaSourceFactoryState(new KafkaContextInitializer[Any, Any, DefinedSingleParameter](Typed[Any], Typed[Any]))
    val source = sourceFactory
      .implementation(Map(KafkaSourceFactory.TopicParamName -> topic),
        List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)), Some(finalState))
      .asInstanceOf[Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef] with ReturningType]
    source
  }

  test("read and deserialize from simple string source") {
    val topic = createTopic("simpleString")
    val givenObj = "sample text"
    val expectedObj = new ConsumerRecord[String, String](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      ConsumerRecord.NULL_CHECKSUM.toLong,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      null,
      givenObj,
      ConsumerRecordUtils.emptyHeaders,
      Optional.of(0)
    )
    pushMessage(new SimpleSerializationSchema[Any](topic, String.valueOf), givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(StringSourceFactory, topic).head.asInstanceOf[ConsumerRecord[String, String]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize from simple json source") {
    val topic = createTopic("simpleJson")
    val givenObj = sampleValue
    val expectedObj = createConsumerRecord[String, SampleValue](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      null,
      givenObj,
      ConsumerRecordUtils.emptyHeaders,
      Optional.of(0)
    )
    pushMessage(new JsonSerializationSchema[SampleValue](topic).asInstanceOf[serialization.KafkaSerializationSchema[Any]], givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(SampleEventSourceFactory, topic).head.asInstanceOf[ConsumerRecord[String, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with value only") {
    val topic = createTopic("consumerRecordNoKey")
    val givenObj = sampleValue
    val expectedObj = createConsumerRecord[String, SampleValue](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      null,
      givenObj,
      ConsumerRecordUtils.emptyHeaders,
      Optional.of(0)
    )
    pushMessage(new JsonSerializationSchema[SampleValue](topic).asInstanceOf[serialization.KafkaSerializationSchema[Any]], givenObj, topic, timestamp = constTimestamp)
    val result = readLastMessage(ConsumerRecordValueSourceFactory, topic).head.asInstanceOf[ConsumerRecord[String, SampleValue]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with key, value and headers") {
    val topic = createTopic("consumerRecordKeyValueHeaders")
    val givenObj = ObjToSerialize(sampleValue, sampleKey, sampleHeadersMap)
    val expectedObj = createConsumerRecord[SampleKey, SampleValue](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      sampleKey,
      sampleValue,
      ConsumerRecordUtils.toHeaders(sampleHeadersMap),
      Optional.of(0)
    )
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
    val serializationSchema = new JsonSerializationSchema[SampleValue](topic).asInstanceOf[serialization.KafkaSerializationSchema[Any]]

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

}

