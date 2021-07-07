package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.flink.api.process.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.source.KafkaSource
import pl.touk.nussknacker.engine.kafka.source.KafkaSource.defaultMaxOutOfOrdernessMillis

/**
  * `createDelayedKafkaSource` is used to create KafkaSource with specified delay.
  * It is used by experimental delayed sources, that unlike delay component, do not use state to keep track of delayed events.
  * This is the optimization for scenarios that require very long delays, where we don't want to keep big state and/or
  * keeping events in state for such long time can result in memory issues.
  * We assume that event timestamps are monotonous. First event is read and when extractTimestamp + delay < processing time the process sleeps.
  * @param timestampAssigner - provides event timestamp to calculate delay, user is allowed to calculate delay in relation to their selected field that represents "business timestamp".
  * @param delay - delay timespan in millis
  */
trait BaseKafkaDelayedSourceFactory {

  protected def createDelayedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                               kafkaConfig: KafkaConfig,
                                               deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                               timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                               formatter: RecordFormatter,
                                               flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]],
                                               delay: Long): KafkaSource[ConsumerRecord[K, V]] = {
    new KafkaSource[ConsumerRecord[K, V]](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {
      override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]] = flinkContextInitializer
      override protected def createFlinkSource(consumerGroupId: String) =
        new DelayedFlinkKafkaConsumer(preparedTopics, deserializationSchema, kafkaConfig, consumerGroupId, delay, timestampAssigner)
    }
  }

  protected def prepareTimestampAssigner[K, V](kafkaConfig: KafkaConfig, extract: (ConsumerRecord[K, V], Long) => Long): TimestampWatermarkHandler[ConsumerRecord[K, V]] = {
    new LegacyTimestampWatermarkHandler[ConsumerRecord[K, V]](
      new BoundedOutOfOrderPreviousElementAssigner[ConsumerRecord[K, V]](
        kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)
      ) {
        override def extractTimestamp(element: ConsumerRecord[K, V], previousElementTimestamp: Long): Long = extract(element, previousElementTimestamp)
      }
    )
  }
}
