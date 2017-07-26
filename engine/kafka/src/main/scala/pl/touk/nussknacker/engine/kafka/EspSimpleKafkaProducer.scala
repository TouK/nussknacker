package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

trait EspSimpleKafkaProducer {
  val kafkaConfig: KafkaConfig

  def sendToKafkaWithNewProducer(topic: String, key: Array[Byte], value: Array[Byte]): Future[RecordMetadata] = {
    var producer: KafkaProducer[Array[Byte], Array[Byte]] = null
    try {
      producer = createProducer()
      sendToKafka(topic, key, value)(producer)
    } finally {
      if (producer != null) {
        producer.close()
      }
    }
  }

  //method with such signature already exists in "net.cakesolutions" %% "scala-kafka-client" % "0.9.0.0" but I struggled to add this dependency...
  def sendToKafka(topic: String, key: Array[Byte], value: Array[Byte])(producer: KafkaProducer[Array[Byte], Array[Byte]]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    producer.send(new ProducerRecord(topic, key, value), producerCallback(promise))
    promise.future
  }

  def createProducer(): KafkaProducer[Array[Byte], Array[Byte]] = {
    new KafkaProducer[Array[Byte], Array[Byte]](KafkaEspUtils.toProducerProperties(kafkaConfig))
  }

  private def producerCallback(promise: Promise[RecordMetadata]): Callback =
    new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        val result = if (exception == null) Success(metadata) else Failure(exception)
        promise.complete(result)
      }
    }
}
