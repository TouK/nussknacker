package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

import scala.reflect.ClassTag

trait RecordFormatterFactory extends Serializable {
  def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter
}

object RecordFormatterFactory {

  def fixedRecordFormatter(formatter: RecordFormatter): RecordFormatterFactory = new RecordFormatterFactory {
    override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = formatter
  }

}