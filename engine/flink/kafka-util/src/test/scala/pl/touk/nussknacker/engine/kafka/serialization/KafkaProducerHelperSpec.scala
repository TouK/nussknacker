package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}

import java.nio.charset.Charset
import java.time.{LocalDateTime, ZoneId}

class KafkaProducerHelperSpec extends FunSuite with Matchers {

  test("should process timestamp") {
    val timestamp = LocalDateTime.of(2021, 3, 29, 11,11).atZone(ZoneId.systemDefault()).toEpochSecond
    createRecord(Long.MinValue).timestamp() shouldBe null
    createRecord(-1).timestamp() shouldBe null
    createRecord(0).timestamp() shouldBe 0
    createRecord(timestamp).timestamp() shouldBe timestamp
  }

  private def createRecord(timestamp: Long): ProducerRecord[Array[Byte], Array[Byte]] = {
    KafkaProducerHelper.createRecord(
      "test", "key".getBytes(Charset.defaultCharset()), "message".getBytes(Charset.defaultCharset()), timestamp
    )
  }

}
