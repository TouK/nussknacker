package pl.touk.nussknacker.engine.kafka.generic

import cats.data.NonEmptyList
import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.metrics.MetricGroup
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.operators.StreamingRuntimeContext
import org.apache.flink.streaming.connectors.kafka
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumerBase
import org.apache.flink.streaming.connectors.kafka.config.OffsetCommitMode
import org.apache.flink.streaming.connectors.kafka.internals.{
  AbstractFetcher,
  KafkaFetcher,
  KafkaTopicPartition,
  KafkaTopicPartitionState
}
import org.apache.flink.streaming.runtime.tasks.ProcessingTimeService
import org.apache.flink.util.SerializedValue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  LegacyTimestampWatermarkHandler,
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.kafka.generic.DelayedFlinkKafkaConsumer.ExtractTimestampForDelay
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.wrapToFlinkDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayCalculator
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaConsumerHandlingExceptions
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic}

import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.function.Consumer
import java.{lang, time, util}
import scala.jdk.CollectionConverters._

object DelayedFlinkKafkaConsumer {

  def apply[T](
      topics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      schema: KafkaDeserializationSchema[T],
      config: KafkaConfig,
      consumerGroupId: String,
      delayCalculator: DelayCalculator,
      timestampAssigner: Option[TimestampWatermarkHandler[T]],
      flinkNodeContext: FlinkCustomNodeContext
  ): FlinkKafkaConsumerBase[T] = {

    val props = KafkaUtils.toConsumerProperties(config, Some(consumerGroupId))

    // Here: partitionState.extractTimestamp works correctly only when WatermarkStrategy is assigned
    // For legacy TimestampAssigners we extract timestamp from Assigner
    def defaultConsumer = new DelayedFlinkKafkaConsumer[T](
      topics,
      schema,
      props,
      delayCalculator,
      (ps, e, t) => ps.extractTimestamp(e, t),
      flinkNodeContext.exceptionHandlerPreparer,
      flinkNodeContext.convertToEngineRuntimeContext,
      NodeId(flinkNodeContext.nodeId)
    )
    timestampAssigner match {
      case Some(lth: LegacyTimestampWatermarkHandler[T]) =>
        new DelayedFlinkKafkaConsumer[T](
          topics,
          schema,
          props,
          delayCalculator,
          (_, e, t) => lth.extractTimestamp(e, t),
          flinkNodeContext.exceptionHandlerPreparer,
          flinkNodeContext.convertToEngineRuntimeContext,
          NodeId(flinkNodeContext.nodeId)
        )
      case Some(sth: StandardTimestampWatermarkHandler[T]) =>
        defaultConsumer.assignTimestampsAndWatermarks(sth.strategy)
      case None => defaultConsumer
    }
  }

  type ExtractTimestampForDelay[T] = (KafkaTopicPartitionState[T, TopicPartition], T, Long) => Long
}

/**
 * Warning: this consumer works correctly only when it's handling a single partition (so job's parallelism must be
 * at least equal to the number of topic partitions). Otherwise, a single message will block reading
 * from multiple partitions, leading bigger delays than intended.
 */
class DelayedFlinkKafkaConsumer[T](
    topics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
    schema: KafkaDeserializationSchema[T],
    props: Properties,
    delayCalculator: DelayCalculator,
    extractTimestamp: ExtractTimestampForDelay[T],
    exceptionHandlerPreparer: RuntimeContext => ExceptionHandler,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    nodeId: NodeId
) extends FlinkKafkaConsumerHandlingExceptions[T](
      topics.map(_.prepared.name).toList.asJava,
      wrapToFlinkDeserializationSchema(schema),
      props,
      exceptionHandlerPreparer,
      convertToEngineRuntimeContext,
      nodeId
    ) {

  @silent("deprecated")
  override def createFetcher(
      sourceContext: SourceFunction.SourceContext[T],
      assignedPartitionsWithInitialOffsets: util.Map[KafkaTopicPartition, lang.Long],
      watermarkStrategy: SerializedValue[WatermarkStrategy[T]],
      runtimeContext: StreamingRuntimeContext,
      offsetCommitMode: OffsetCommitMode,
      consumerMetricGroup: MetricGroup,
      useMetrics: Boolean
  ): AbstractFetcher[T, _] = {

    if ((offsetCommitMode eq OffsetCommitMode.ON_CHECKPOINTS) || (offsetCommitMode eq OffsetCommitMode.DISABLED)) {
      properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }

    new DelayedKafkaFetcher(
      sourceContext,
      assignedPartitionsWithInitialOffsets,
      watermarkStrategy,
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
      delayCalculator,
      extractTimestamp
    )
  }

}

object DelayedKafkaFetcher {
  private val maxSleepTime = time.Duration.of(30, ChronoUnit.SECONDS).toMillis
}

class DelayedKafkaFetcher[T](
    sourceContext: SourceFunction.SourceContext[T],
    assignedPartitionsWithInitialOffsets: util.Map[KafkaTopicPartition, lang.Long],
    watermarkStrategy: SerializedValue[WatermarkStrategy[T]],
    processingTimeProvider: ProcessingTimeService,
    autoWatermarkInterval: lang.Long,
    userCodeClassLoader: ClassLoader,
    taskNameWithSubtasks: String,
    metricGroup: MetricGroup,
    consumerMetricGroup: MetricGroup,
    deserializer: kafka.KafkaDeserializationSchema[T],
    kafkaProperties: Properties,
    pollTimeout: lang.Long,
    useMetrics: Boolean,
    delayCalculator: DelayCalculator,
    extractTimestamp: ExtractTimestampForDelay[T]
) extends KafkaFetcher[T](
      sourceContext,
      assignedPartitionsWithInitialOffsets,
      watermarkStrategy,
      processingTimeProvider,
      autoWatermarkInterval,
      userCodeClassLoader,
      taskNameWithSubtasks,
      deserializer,
      kafkaProperties,
      pollTimeout,
      metricGroup,
      consumerMetricGroup,
      useMetrics
    )
    with LazyLogging {
  import DelayedKafkaFetcher._

  override def emitRecordsWithTimestamps(
      records: util.Queue[T],
      partitionState: KafkaTopicPartitionState[T, TopicPartition],
      offset: Long,
      kafkaEventTimestamp: Long
  ): Unit = {
    var maxEventTimestamp = 0L
    records.forEach(new Consumer[T] {
      override def accept(r: T): Unit = {
        val recordTimestamp = extractTimestamp(partitionState, r, kafkaEventTimestamp)
        if (recordTimestamp > maxEventTimestamp) {
          maxEventTimestamp = recordTimestamp
        }
      }
    })

    var currentDelay = 0L
    val delay = delayCalculator.calculateDelay(processingTimeProvider.getCurrentProcessingTime, maxEventTimestamp)
    while (delay > currentDelay) {
      val remainingDelay = delay - currentDelay
      val sleepTime      = Math.min(maxSleepTime, remainingDelay)

      if (sleepTime >= maxSleepTime) {
        val logMessage = s"Sleeping for $sleepTime ms of total $remainingDelay ms for ${records.size()} events. " +
          s"Max event timestamp is $maxEventTimestamp, fetcher delay is $delay, topic:partition:offset is " +
          s"${partitionState.getTopic}:${partitionState.getPartition}:$offset"
        logger.info(logMessage)
      }

      Thread.sleep(sleepTime)
      currentDelay += sleepTime
    }

    super.emitRecordsWithTimestamps(records, partitionState, offset, kafkaEventTimestamp)
  }

}
