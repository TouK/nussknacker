package pl.touk.nussknacker.engine.avro.source

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroDeserializationSchemaFactory
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka.source.KafkaSource
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatterFactory}

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[K: ClassTag, V: ClassTag](timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends FlinkSourceFactory[V] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  def createSource(preparedTopic: PreparedKafkaTopic,
                   kafkaConfig: KafkaConfig,
                   deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                   recordFormatterFactory: RecordFormatterFactory,
                   keySchemaDataUsedInRuntime: Option[RuntimeSchemaData],
                   valueSchemaUsedInRuntime: Option[RuntimeSchemaData],
                   flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]])
                  (implicit processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[ConsumerRecord[K, V]] = {

    // prepare KafkaDeserializationSchema based on key and value schema
    val deserializationSchema = deserializationSchemaFactory.create[K, V](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime)
    val recordFormatter = recordFormatterFactory.create[ConsumerRecord[K, V]](kafkaConfig, deserializationSchema)

    new KafkaSource[ConsumerRecord[K, V]](
      List(preparedTopic),
      kafkaConfig,
      deserializationSchema,
      assignerToUse(kafkaConfig),
      recordFormatter
    ) {
      override val contextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]] = flinkContextInitializer
    }
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] = {
    Some(
      timestampAssigner.getOrElse(
        new LegacyTimestampWatermarkHandler[ConsumerRecord[K, V]](
          new BoundedOutOfOrderPreviousElementAssigner[ConsumerRecord[K, V]](
            kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)
          )
        )
      )
    )
  }
}
