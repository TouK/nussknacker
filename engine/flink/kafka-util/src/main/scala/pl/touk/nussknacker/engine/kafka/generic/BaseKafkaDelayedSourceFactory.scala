package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.flink.api.process.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

trait BaseKafkaDelayedSourceFactory {

  protected def createDelayedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                               kafkaConfig: KafkaConfig,
                                               deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                               timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                               formatter: RecordFormatter,
                                               flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]],
                                               delay: Long,
                                               extractTimestamp: (ConsumerRecord[K, V], Long) => Long): KafkaSource[ConsumerRecord[K, V]] = {
    new KafkaSource[ConsumerRecord[K, V]](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {
      override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]] = flinkContextInitializer
      override protected def createFlinkSource(consumerGroupId: String) =
        new DelayedFlinkKafkaConsumer(preparedTopics, deserializationSchema, kafkaConfig, consumerGroupId, extractTimestamp, delay)
    }
  }
}
