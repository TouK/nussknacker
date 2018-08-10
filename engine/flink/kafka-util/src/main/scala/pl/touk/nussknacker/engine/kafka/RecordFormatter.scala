package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

/**
  * It is interface for bi-directional conversion between Kafka record and bytes. It is used when data
  * stored on topic aren't in human readable format and you need to add extra step in generation of test data
  * and in reading of these data.
  */
trait RecordFormatter {

  def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte]

  def parseRecord(bytes: Array[Byte]): ProducerRecord[Array[Byte], Array[Byte]]

}