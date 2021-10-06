package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.producer.{KafkaProducer, MockProducer, Producer}

object KafkaProducerCreator {

  type Binary = KafkaProducerCreator[Array[Byte], Array[Byte]]

}

/*
  The main reason for this class is to be able to use Kafka's MockProducer more easily. We don't want to pass Producer directly,
  as it has lifecycle that needs to be handled carefully
 */
trait KafkaProducerCreator[K, V] {
  def createProducer(clientId: String): Producer[K, V]
}

case class DefaultProducerCreator[K, V](kafkaConfig: KafkaConfig) extends KafkaProducerCreator[K, V] {
  override def createProducer(clientId: String): Producer[K, V] = {
    new KafkaProducer[K, V](KafkaUtils.toProducerProperties(kafkaConfig, clientId))
  }
}

case class MockProducerCreator[K, V](mockProducer: MockProducer[K, V]) extends KafkaProducerCreator[K, V] {
  override def createProducer(clientId: String): Producer[K, V] = mockProducer
}
