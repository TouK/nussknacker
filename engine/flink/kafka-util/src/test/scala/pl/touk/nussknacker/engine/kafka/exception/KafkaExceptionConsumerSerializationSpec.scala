package pl.touk.nussknacker.engine.kafka.exception

import org.apache.kafka.clients.producer.MockProducer
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.MockProducerCreator

import java.time.Instant
import scala.jdk.CollectionConverters.asScalaBufferConverter

class KafkaExceptionConsumerSerializationSpec extends FunSuite with Matchers {

  private val mockProducer = new MockProducer[Array[Byte], Array[Byte]]()

  private val metaData = MetaData("test", StreamMetaData())

  private val consumerConfig = KafkaExceptionConsumerConfig("mockTopic",
    stackTraceLengthLimit = 20,
    includeHost = true,
    includeInputEvent = false,
    additionalParams = Map("testValue" -> "1"))

  private val exception = NuExceptionInfo(Some("nodeId"), Some("componentName"), Some("componentType"), NonTransientException("input1", "mess", Instant.ofEpochMilli(111)), Context("ctxId"))

  private val serializationSchema = KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)

  private val consumer = TempProducerKafkaExceptionConsumer(serializationSchema, MockProducerCreator(mockProducer))

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
