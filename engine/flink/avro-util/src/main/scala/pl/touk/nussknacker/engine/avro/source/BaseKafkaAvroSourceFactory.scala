package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroDeserializationSchemaFactory
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[T: ClassTag](timestampAssigner: Option[TimestampWatermarkHandler[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  def createSource(preparedTopic: PreparedKafkaTopic,
                   kafkaConfig: KafkaConfig,
                   deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                   recordFormatterFactory: RecordFormatterFactory,
                   keySchemaDataUsedInRuntime: Option[RuntimeSchemaData],
                   valueSchemaUsedInRuntime: Option[RuntimeSchemaData])
                  (implicit processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] = {

    // prepare KafkaDeserializationSchema based on key and value schema
    // TODO: add key-value deserialization as default scenario: create[K, V]
    val deserializationSchema = deserializationSchemaFactory.create[Any, T](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime).asInstanceOf[KafkaDeserializationSchema[T]]
    val recordFormatter = recordFormatterFactory.create[T](kafkaConfig, deserializationSchema)

    new KafkaSource(
      List(preparedTopic),
      kafkaConfig,
      deserializationSchema,
      assignerToUse(kafkaConfig),
      recordFormatter
    )
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampWatermarkHandler[T]] = {
    Some(timestampAssigner.getOrElse(
      new LegacyTimestampWatermarkHandler[T](new BoundedOutOfOrderPreviousElementAssigner[T](kafkaConfig.defaultMaxOutOfOrdernessMillis
        .getOrElse(defaultMaxOutOfOrdernessMillis))
    )))
  }
}
