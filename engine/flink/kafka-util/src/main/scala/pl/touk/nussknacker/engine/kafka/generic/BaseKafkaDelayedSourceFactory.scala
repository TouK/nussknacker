package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.flink.api.process.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.kafka.source.KafkaSource.defaultMaxOutOfOrdernessMillis
import pl.touk.nussknacker.engine.kafka.source.{ConsumerRecordBasedKafkaSource, KafkaSource}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import java.time.Duration

/**
  * `createDelayedKafkaSource` is used to create KafkaSource with specified fixed delay (eg 2 hours).
  * `createScheduledKafkaSource` is used to create KafkaSource that runs according to defined schedule (eg every Monday from 8AM to 4PM)
  * It is used by experimental delayed sources, that unlike delay component, do not use state to keep track of delayed events.
  * This is the optimization for scenarios that require very long delays, where we don't want to keep big state and/or
  * keeping events in state for such long time can result in memory issues.
  * We assume that event timestamps are monotonous. First event is read and when extractTimestamp + delay < processing time the process sleeps.
  * @param timestampAssigner - provides event timestamp to calculate delay, user is allowed to calculate delay in relation to their selected field that represents "business timestamp".
  * @param delay - delay timespan in millis
  * @param schedule - schedule that defines when to process events
  */
trait BaseKafkaDelayedSourceFactory {

  protected def createDelayedKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                               kafkaConfig: KafkaConfig,
                                               deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                               timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                               formatter: RecordFormatter,
                                               flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]],
                                               delay: Long): KafkaSource[ConsumerRecord[K, V]] = {
    val delayProvider = new FixedDelayProvider(delay)
    new ConsumerRecordBasedKafkaSource[K, V](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer) {
      override protected def createFlinkSource(consumerGroupId: String) =
        new DelayedFlinkKafkaConsumer(preparedTopics, deserializationSchema, kafkaConfig, consumerGroupId, delayProvider, timestampAssigner)
    }
  }

  protected def createScheduledKafkaSource[K, V](preparedTopics: List[PreparedKafkaTopic],
                                                 kafkaConfig: KafkaConfig,
                                                 deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                                 timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                                 formatter: RecordFormatter,
                                                 flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]],
                                                 schedule: Schedule): KafkaSource[ConsumerRecord[K, V]] = {
    val delayProvider = new ScheduleDelayProvider(schedule)
    new ConsumerRecordBasedKafkaSource[K, V](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer) {
      override protected def createFlinkSource(consumerGroupId: String) =
        new DelayedFlinkKafkaConsumer(preparedTopics, deserializationSchema, kafkaConfig, consumerGroupId, delayProvider, timestampAssigner)
    }
  }

  protected def prepareTimestampAssigner[K, V](kafkaConfig: KafkaConfig, extract: (ConsumerRecord[K, V], Long) => Long): TimestampWatermarkHandler[ConsumerRecord[K, V]] = {
    StandardTimestampWatermarkHandler.boundedOutOfOrderness(extract, Duration.ofMillis(kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)))
  }
}
