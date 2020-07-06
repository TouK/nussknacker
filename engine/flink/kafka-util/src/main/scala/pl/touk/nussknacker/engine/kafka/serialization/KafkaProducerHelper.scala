package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord
import java.lang

object KafkaProducerHelper {

  def createRecord(topic: String, key: Array[Byte], value: Array[Byte], timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
    //Kafka timestamp has to be >= 0, while Flink can use Long.MinValue
    val timestampToSerialize: lang.Long = Option(timestamp).map(Math.max(0L, _): lang.Long).orNull
    new ProducerRecord[Array[Byte], Array[Byte]](topic, null,
      timestampToSerialize,
      key,
      value
    )
  }

}
