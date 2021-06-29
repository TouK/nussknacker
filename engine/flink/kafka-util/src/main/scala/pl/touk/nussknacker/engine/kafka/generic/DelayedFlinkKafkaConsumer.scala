package pl.touk.nussknacker.engine.kafka.generic

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.metrics.MetricGroup
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.operators.StreamingRuntimeContext
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, KafkaDeserializationSchema}
import org.apache.flink.streaming.connectors.kafka.config.OffsetCommitMode
import org.apache.flink.streaming.connectors.kafka.internal.KafkaFetcher
import org.apache.flink.streaming.connectors.kafka.internals.{AbstractFetcher, KafkaTopicPartition, KafkaTopicPartitionState}
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeService
import org.apache.flink.util.SerializedValue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic}

import java.time.temporal.ChronoUnit
import java.{lang, time, util}
import java.util.Properties
import scala.collection.JavaConverters._

class DelayedFlinkKafkaConsumer[T](topics: List[PreparedKafkaTopic],
                                   schema: KafkaDeserializationSchema[T],
                                   config: KafkaConfig,
                                   consumerGroupId: String,
                                   timeExtract: (T, Long) => Long,
                                   delay: Long)
  extends FlinkKafkaConsumer[T](topics.map(_.prepared).asJava, schema, KafkaUtils.toProperties(config, Some(consumerGroupId))) {

  override def createFetcher(sourceContext: SourceFunction.SourceContext[T],
                             assignedPartitionsWithInitialOffsets: util.Map[KafkaTopicPartition, lang.Long],
                             watermarkStrategy: SerializedValue[WatermarkStrategy[T]],
                             runtimeContext: StreamingRuntimeContext,
                             offsetCommitMode: OffsetCommitMode,
                             consumerMetricGroup: MetricGroup,
                             useMetrics: Boolean): AbstractFetcher[T, _] = {

    if ((offsetCommitMode eq OffsetCommitMode.ON_CHECKPOINTS) || (offsetCommitMode eq OffsetCommitMode.DISABLED)) {
      properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }

    new DelayedKafkaFetcher(sourceContext, assignedPartitionsWithInitialOffsets, watermarkStrategy,
      runtimeContext.getProcessingTimeService,
      runtimeContext.getExecutionConfig.getAutoWatermarkInterval,
      runtimeContext.getUserCodeClassLoader,
      runtimeContext.getTaskNameWithSubtasks,
      runtimeContext.getMetricGroup,
      consumerMetricGroup,
      deserializer,
      properties,
      pollTimeout,
      useMetrics,
      timeExtract, delay)
  }
}

object DelayedKafkaFetcher {
  private val maxSleepTime = time.Duration.of(30, ChronoUnit.SECONDS).toMillis
}

class DelayedKafkaFetcher[T](sourceContext: SourceFunction.SourceContext[T],
                             assignedPartitionsWithInitialOffsets: util.Map[KafkaTopicPartition, lang.Long],
                             watermarkStrategy: SerializedValue[WatermarkStrategy[T]],
                             processingTimeProvider: ProcessingTimeService,
                             autoWatermarkInterval: lang.Long,
                             userCodeClassLoader: ClassLoader,
                             taskNameWithSubtasks: String,
                             metricGroup: MetricGroup,
                             consumerMetricGroup: MetricGroup,
                             deserializer: KafkaDeserializationSchema[T],
                             kafkaProperties: Properties,
                             pollTimeout: lang.Long,
                             useMetrics: Boolean,
                             timeExtract: (T, Long) => Long,
                             delay: Long) extends KafkaFetcher[T](sourceContext, assignedPartitionsWithInitialOffsets, watermarkStrategy,
  processingTimeProvider, autoWatermarkInterval, userCodeClassLoader, taskNameWithSubtasks, deserializer, kafkaProperties, pollTimeout, metricGroup, consumerMetricGroup, useMetrics) with LazyLogging {
  import DelayedKafkaFetcher._

  override def emitRecordsWithTimestamps(records: util.Queue[T],
                                         partitionState: KafkaTopicPartitionState[T, TopicPartition],
                                         offset: Long,
                                         kafkaEventTimestamp: Long): Unit = {
    var maxEventTimestamp = 0L
    records.forEach(r => {
      val recordTimestamp = timeExtract(r, kafkaEventTimestamp)
      if(recordTimestamp > maxEventTimestamp){
        maxEventTimestamp = recordTimestamp
      }
    })

    var latency = processingTimeProvider.getCurrentProcessingTime - maxEventTimestamp

    while (delay > latency) {
      val eventDelay = delay - latency
      val sleepTime = Math.min(maxSleepTime, eventDelay)

      val logMessage = s"Sleeping for $sleepTime ms of total $eventDelay ms for ${records.size()} events. Max event timestamp is $maxEventTimestamp, fetcher delay is $delay."

      if (sleepTime < maxSleepTime) {
        logger.debug(logMessage)
      } else {
        logger.info(logMessage)
      }

      Thread.sleep(sleepTime)
      latency += sleepTime
    }

    super.emitRecordsWithTimestamps(records, partitionState, offset, kafkaEventTimestamp)
  }
}
