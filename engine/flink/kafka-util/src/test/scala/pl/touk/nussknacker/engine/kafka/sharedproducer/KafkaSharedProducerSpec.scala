package pl.touk.nussknacker.engine.kafka.sharedproducer

import org.apache.kafka.clients.producer.{MockProducer, ProducerRecord}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.kafka.MockProducerCreator

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits._
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

class KafkaSharedProducerSpec extends FunSuite with Matchers {

  test("should close producer after return") {

    val mockProducer = new MockProducer[Array[Byte], Array[Byte]]()
    val creator = MockProducerCreator(mockProducer)

    val service = SharedKafkaProducerHolder.retrieveService(creator)(MetaData("id", StreamMetaData()))
    val service2 = SharedKafkaProducerHolder.retrieveService(creator)(MetaData("id", StreamMetaData()))

    service.sendToKafka(new ProducerRecord("t1", Array[Byte](),"testValue1".getBytes(StandardCharsets.UTF_8)))
    service2.sendToKafka(new ProducerRecord("t2", Array[Byte](),"testValue2".getBytes(StandardCharsets.UTF_8)))


    mockProducer.closed() shouldBe false
    service.close()
    mockProducer.closed() shouldBe false
    service2.close()
    mockProducer.closed() shouldBe true

    val values = mockProducer.history().asScala.toList.map(mes => (mes.topic(), new String(mes.value())))
    values shouldBe List(("t1", "testValue1"), ("t2", "testValue2"))

  }

}
