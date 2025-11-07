package pl.touk.nussknacker.engine.kafka.exception

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.kafka.MockProducerCreator

import java.time.Instant
import scala.jdk.CollectionConverters._

class KafkaExceptionConsumerSerializationSpec extends AnyFunSuite with Matchers {

  private val mockProducer =
    new MockProducer[Array[Byte], Array[Byte]](false, new ByteArraySerializer, new ByteArraySerializer)

  private val metaData = MetaData("test", StreamMetaData())

  private val consumerConfig = KafkaExceptionConsumerConfig(
    "mockTopic",
    stackTraceLengthLimit = 20,
    includeHost = true,
    includeInputEvent = true,
    additionalParams = Map("testValue" -> "1")
  )

  private val variables = Map(VariableConstants.InputVariableName -> Map("name" -> "lcl", "age" -> 36))

  private val context = Context(ContextId.dummy, variables, None, Some(TraceId.generate()))

  private val exception = NuExceptionInfo(
    Some(NodeComponentInfo("nodeId", ComponentType.Service, "componentName")),
    new Exception("mess"),
    context,
    "input1",
    Instant.ofEpochMilli(111)
  )

  private val serializationSchema = new KafkaJsonExceptionSerializationSchema(metaData, consumerConfig)

  private val consumer =
    TempProducerKafkaExceptionConsumer(
      metaData,
      serializationSchema,
      MockProducerCreator(mockProducer),
      NoopKafkaErrorTopicInitializer
    )

  test("records event") {
    consumer.consume(exception)

    val message = mockProducer.history().asScala.toList match {
      case one :: Nil => one
      case other      => fail(s"Expected one message, got $other")
    }
    message.topic() shouldBe consumerConfig.topic
    new String(message.key()) shouldBe "test-nodeId"
    val decodedPayload = CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](message.value())

    decodedPayload.processName shouldBe metaData.name
    decodedPayload.nodeId shouldBe Some("nodeId")
    decodedPayload.exceptionInput shouldBe Some("input1")
    decodedPayload.message shouldBe Some("mess")
    decodedPayload.timestamp shouldBe 111
    decodedPayload.inputEvent shouldBe Some(ToJsonEncoder.default.encodeUnsafe(variables))
    decodedPayload.additionalData shouldBe Map("testValue" -> "1")
  }

}
