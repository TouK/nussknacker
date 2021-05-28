package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroDeserializationSchemaFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
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
                   createRecordFormatter: RecordFormatter,
                   keySchemaDataUsedInRuntime: Option[RuntimeSchemaData],
                   valueSchemaUsedInRuntime: Option[RuntimeSchemaData])
                  (implicit processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] = {

    // prepare KafkaDeserializationSchema based on key and value schema
    // TODO: add key-value deserialization as default scenario: create[K, V]
    val deserializationSchema = deserializationSchemaFactory.create[Any, T](kafkaConfig, keySchemaDataUsedInRuntime, valueSchemaUsedInRuntime).asInstanceOf[KafkaDeserializationSchema[T]]

    if (AvroUtils.isSpecificRecord[T]) {
      new KafkaSource(
        List(preparedTopic),
        kafkaConfig,
        deserializationSchema,
        assignerToUse(kafkaConfig),
        createRecordFormatter
      )
    } else {
      new KafkaSource(
        List(preparedTopic),
        kafkaConfig,
        deserializationSchema,
        assignerToUse(kafkaConfig),
        createRecordFormatter
      ) with ReturningType {
        override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(valueSchemaUsedInRuntime.get.schema)
      }
    }
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampWatermarkHandler[T]] = {
    Some(timestampAssigner.getOrElse(
      new LegacyTimestampWatermarkHandler[T](new BoundedOutOfOrderPreviousElementAssigner[T](kafkaConfig.defaultMaxOutOfOrdernessMillis
        .getOrElse(defaultMaxOutOfOrdernessMillis))
    )))
  }
}
