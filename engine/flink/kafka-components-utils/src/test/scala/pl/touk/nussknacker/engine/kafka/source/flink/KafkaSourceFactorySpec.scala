package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, Params, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceFactoryState
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin._
import pl.touk.nussknacker.engine.kafka.source.{KafkaContextInitializer, KafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaRecordUtils, KafkaSpec}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.Optional

class KafkaSourceFactorySpec
    extends AnyFunSuite
    with Matchers
    with KafkaSpec
    with PatientScalaFutures
    with KafkaSourceFactoryMixin {

  private lazy val metaData: MetaData = MetaData("mock-id", StreamMetaData())

  private lazy val nodeId: NodeId = NodeId("mock-node-id")

  private def readLastMessage(
      sourceFactory: KafkaSourceFactory[Any, Any],
      topic: String,
      numberOfMessages: Int = 1
  ): List[AnyRef] = {
    val source   = createSource(sourceFactory, topic)
    val testData = source.generateTestData(numberOfMessages)
    source.testRecordParser.parse(testData.testRecords)
  }

  private def createSource[K, V](
      sourceFactory: KafkaSourceFactory[K, V],
      topic: String
  ): Source with TestDataGenerator with FlinkSourceTestSupport[ConsumerRecord[K, V]] with ReturningType = {
    val finalState = KafkaSourceFactoryState(
      new KafkaContextInitializer[K, V](VariableConstants.InputVariableName, Typed[Any], Typed[Any])
    )
    val source = sourceFactory
      .implementation(
        Params(Map(TopicParamName -> topic)),
        List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)),
        Some(finalState)
      )
      .asInstanceOf[Source with TestDataGenerator with FlinkSourceTestSupport[ConsumerRecord[K, V]] with ReturningType]
    source
  }

  test("read and deserialize from simple string source") {
    val topic    = createTopic("simpleString")
    val givenObj = "sample text"
    val expectedObj = new ConsumerRecord[String, String](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      null,
      givenObj,
      KafkaRecordUtils.emptyHeaders,
      Optional.of(0: Integer)
    )
    pushMessage(new SimpleSerializationSchema[Any](topic, String.valueOf), givenObj, timestamp = constTimestamp)
    val result = readLastMessage(StringSourceFactory, topic).head.asInstanceOf[ConsumerRecord[String, String]]
    checkResult(result, expectedObj)
  }

  test("read and deserialize consumer record with key, value and headers") {
    val topic    = createTopic("consumerRecordKeyValueHeaders")
    val givenObj = ObjToSerialize(sampleValue, sampleKey, sampleHeadersMap)
    val expectedObj = createConsumerRecord[SampleKey, SampleValue](
      topic,
      0,
      0L,
      constTimestamp,
      TimestampType.CREATE_TIME,
      sampleKey,
      sampleValue,
      KafkaRecordUtils.toHeaders(sampleHeadersMap),
      Optional.of(0)
    )
    pushMessage(objToSerializeSerializationSchema(topic), givenObj, timestamp = constTimestamp)
    val result = readLastMessage(ConsumerRecordKeyValueSourceFactory, topic).head
      .asInstanceOf[ConsumerRecord[SampleKey, SampleValue]]
    checkResult(result, expectedObj)
  }

}
