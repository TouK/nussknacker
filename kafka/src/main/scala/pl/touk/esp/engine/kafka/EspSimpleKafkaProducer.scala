package pl.touk.esp.engine.kafka

import java.util.concurrent.Future

import org.apache.kafka.clients.producer.internals.ErrorLoggingCallback
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

trait EspSimpleKafkaProducer {
  val kafkaConfig: KafkaConfig

  def sendToKafkaWithNewProducer(key: Array[Byte], value: Array[Byte], topic: String): Future[RecordMetadata] = {
    var producer: KafkaProducer[Array[Byte], Array[Byte]] = null
    try {
      producer = createProducer()
      sendToKafka(key, value, topic)(producer)
    } finally {
      if (producer != null) {
        producer.close()
      }
    }
  }

  def sendToKafka(key: Array[Byte], value: Array[Byte], topic: String)(producer: KafkaProducer[Array[Byte], Array[Byte]]): Future[RecordMetadata] = {
    val errorLoggingCallback = new ErrorLoggingCallback(topic, key, value, true)
    producer.send(new ProducerRecord(topic, value), errorLoggingCallback)
  }

  def createProducer(): KafkaProducer[Array[Byte], Array[Byte]] = {
    new KafkaProducer[Array[Byte], Array[Byte]](KafkaEspUtils.toProducerProperties(kafkaConfig))
  }

}
