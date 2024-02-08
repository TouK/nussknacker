package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.{KafkaSourceImplFactory, KafkaTestParametersInfo}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

class FlinkKafkaSourceImplFactory[K, V](
    protected val timestampAssigner: Option[TimestampWatermarkHandler[Context]]
) extends KafkaSourceImplFactory[K, V]
    with Serializable {

  override def createSource(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: List[PreparedKafkaTopic],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: RecordFormatter,
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo
  ): Source =
    new FlinkConsumerRecordBasedKafkaSource[K, V](
      preparedTopics,
      kafkaConfig,
      deserializationSchema,
      timestampAssigner,
      formatter,
      contextInitializer,
      testParametersInfo
    )

}
