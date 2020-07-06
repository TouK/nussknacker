package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord

object KafkaProducerHelper {

  def createRecord(topic: String, key: Array[Byte], value: Array[Byte], timestamp: Long): ProducerRecord[Array[Byte], Array[Byte]] = {
    //Kafka timestamp has to be >= 0, while Flink can use Long.MinValue
    val timestampToSerialize: java.lang.Long = Math.max(0L, timestamp)
    new ProducerRecord[Array[Byte], Array[Byte]](topic, null,
      timestampToSerialize,
      key,
      value
    )
  }

}
