package pl.touk.nussknacker.engine.kafka.generic

import cats.data.NonEmptyList
import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source, TopicName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory.{
  extractDelayInMillis,
  extractTimestampField
}
import pl.touk.nussknacker.engine.kafka.source.delayed.{DelayCalculator, FixedDelayCalculator}
import pl.touk.nussknacker.engine.kafka.source.flink.{
  FlinkConsumerRecordBasedKafkaSource,
  FlinkKafkaSource,
  FlinkKafkaSourceImplFactory
}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

/**
  * `createDelayedKafkaSourceWithFixedDelay` is used to create KafkaSource with specified fixed delay (eg 2 hours).
  * `createDelayedKafkaSource` is used to create KafkaSource that delays events using custom `DelayCalculator`
  * It is used by experimental delayed sources, that unlike delay component, do not use state to keep track of delayed events.
  * This is the optimization for scenarios that require very long delays, where we don't want to keep big state and/or
  * keeping events in state for such long time can result in memory issues.
  * We assume that event timestamps are monotonous. First event is read and when extractTimestamp + delay < processing time the process sleeps.
  *
  * @param timestampAssigner - provides event timestamp to calculate delay, user is allowed to calculate delay in relation to their selected field that represents "business timestamp".
  */
class FlinkKafkaDelayedSourceImplFactory[K, V](
    timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
    extractTimestampFromField: String => SerializableTimestampAssigner[ConsumerRecord[K, V]]
) extends FlinkKafkaSourceImplFactory[K, V](timestampAssigner) {

  override def createSource(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: RecordFormatter,
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      namingStrategy: NamingStrategy
  ): Source = {
    extractDelayInMillis(params) match {
      case Some(millis) if millis > 0 =>
        val timestampFieldName = extractTimestampField(params)
        val timestampAssignerWithExtract: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] =
          timestampFieldName
            .map(fieldName => prepareTimestampAssigner(kafkaConfig, extractTimestampFromField(fieldName)))
            .orElse(timestampAssigner)
        createDelayedKafkaSourceWithFixedDelay(
          preparedTopics,
          kafkaConfig,
          deserializationSchema,
          timestampAssignerWithExtract,
          formatter,
          contextInitializer,
          testParametersInfo,
          millis,
          namingStrategy
        )
      case _ =>
        super.createSource(
          params,
          dependencies,
          finalState,
          preparedTopics,
          kafkaConfig,
          deserializationSchema,
          formatter,
          contextInitializer,
          testParametersInfo,
          namingStrategy
        )
    }
  }

  protected def createDelayedKafkaSourceWithFixedDelay(
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
      formatter: RecordFormatter,
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      delay: Long,
      namingStrategy: NamingStrategy
  ): FlinkKafkaSource[ConsumerRecord[K, V]] = {
    val delayCalculator = new FixedDelayCalculator(delay)
    createDelayedKafkaSource(
      preparedTopics,
      kafkaConfig,
      deserializationSchema,
      timestampAssigner,
      formatter,
      contextInitializer,
      testParametersInfo,
      delayCalculator,
      namingStrategy
    )
  }

  protected def createDelayedKafkaSource(
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
      formatter: RecordFormatter,
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      delayCalculator: DelayCalculator,
      namingStrategy: NamingStrategy
  ): FlinkKafkaSource[ConsumerRecord[K, V]] = {
    new FlinkConsumerRecordBasedKafkaSource[K, V](
      preparedTopics,
      kafkaConfig,
      deserializationSchema,
      timestampAssigner,
      formatter,
      contextInitializer,
      testParametersInfo,
      namingStrategy
    ) {

      @silent("deprecated")
      override protected def createFlinkSource(
          consumerGroupId: String,
          flinkNodeContext: FlinkCustomNodeContext
      ): SourceFunction[ConsumerRecord[K, V]] =
        DelayedFlinkKafkaConsumer(
          preparedTopics,
          deserializationSchema,
          this.kafkaConfig,
          consumerGroupId,
          delayCalculator,
          this.timestampAssigner,
          flinkNodeContext
        )

    }
  }

  protected def prepareTimestampAssigner(
      kafkaConfig: KafkaConfig,
      extract: SerializableTimestampAssigner[ConsumerRecord[K, V]]
  ): TimestampWatermarkHandler[ConsumerRecord[K, V]] = {
    StandardTimestampWatermarkHandler.boundedOutOfOrderness(
      Some(extract),
      kafkaConfig.defaultMaxOutOfOrdernessMillis,
      kafkaConfig.idleTimeoutDuration
    )
  }

}
