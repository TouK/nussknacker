package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord
import java.lang

import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

object KafkaProducerHelper {

  def createRecord(topic: String, key: Array[Byte], value: Array[Byte], timestamp: lang.Long, headers: Headers = ConsumerRecordUtils.emptyHeaders): ProducerRecord[Array[Byte], Array[Byte]] = {
    //Flink can use Long.MinValue (see StreamRecord.getTimestamp), we set it to null to let Kafka producer decide (probably set to currentTimeMillis)
    val timestampToSerialize: lang.Long = Option(timestamp).filter(_ >= 0).orNull
    new ProducerRecord[Array[Byte], Array[Byte]](topic, null,
      timestampToSerialize,
      key,
      value,
      headers
    )
  }

}
