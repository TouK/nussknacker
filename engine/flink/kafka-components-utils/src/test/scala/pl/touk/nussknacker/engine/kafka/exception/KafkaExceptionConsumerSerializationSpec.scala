package pl.touk.nussknacker.engine.kafka.exception

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.MockProducerCreator

import java.time.Instant
import scala.jdk.CollectionConverters._

class KafkaExceptionConsumerSerializationSpec extends AnyFunSuite with Matchers {

  private val mockProducer = new MockProducer[Array[Byte], Array[Byte]](false, new ByteArraySerializer, new ByteArraySerializer)

  private val metaData = MetaData("test", StreamMetaData())

  private val consumerConfig = KafkaExceptionConsumerConfig("mockTopic",
    stackTraceLengthLimit = 20,
    includeHost = true,
    includeInputEvent = false,
    additionalParams = Map("testValue" -> "1"))

  private val exception = NuExceptionInfo(Some(NodeComponentInfo("nodeId", "componentName", ComponentType.Enricher)), NonTransientException("input1", "mess", Instant.ofEpochMilli(111)), Context("ctxId"))

  private val serializationSchema = new KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)

  //null as we don't test open here...
  private val consumer = TempProducerKafkaExceptionConsumer(serializationSchema, MockProducerCreator(mockProducer), null)

  test("records event") {
    consumer.consume(exception)

    val message = mockProducer.history().asScala.toList match {
      case one :: Nil => one
      case other => fail(s"Expected one message, got $other")
    }
    message.topic() shouldBe consumerConfig.topic
    new String(message.key()) shouldBe "test-nodeId"
    val decodedPayload = CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](message.value())

    decodedPayload.processName shouldBe metaData.id
    decodedPayload.nodeId shouldBe Some("nodeId")
    decodedPayload.exceptionInput shouldBe Some("input1")
    decodedPayload.message shouldBe Some("mess")
    decodedPayload.timestamp shouldBe 111
    decodedPayload.additionalData shouldBe Map("testValue" -> "1")
  }

}
