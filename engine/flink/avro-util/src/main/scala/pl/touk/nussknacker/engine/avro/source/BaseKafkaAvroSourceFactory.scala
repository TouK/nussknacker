package pl.touk.nussknacker.engine.avro.source

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.context.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.util.timestamp.BoundedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[T: ClassTag](timestampAssigner: Option[TimestampWatermarkHandler[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  protected var customContextInitializer: FlinkContextInitializer[T] = _

  def createSource(preparedTopic: PreparedKafkaTopic,
                   kafkaConfig: KafkaConfig,
                   deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                   createRecordFormatter: RecordFormatter,
                   valueSchemaDeterminer: AvroSchemaDeterminer,
                   keySchemaDeterminer: AvroSchemaDeterminer,
                   returnGenericAvroType: Boolean,
                   valueClassTagOpt: Option[ClassTag[_]] = None,
                   keyClassTagOpt: Option[ClassTag[_]] = None)
                  (implicit processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] = {

    // value schema
    val valueSchemaData = valueSchemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val valueSchemaUsedInRuntime = valueSchemaDeterminer.toRuntimeSchema(valueSchemaData)
    // key schema, optional
    val keySchemaDataUsedInRuntime = Option(keySchemaDeterminer).flatMap(_.determineSchemaUsedInTyping.toOption)

    // prepare KafkaDeserializationSchema based on key and value schema
    val deserializationSchema = deserializationSchemaFactory.create[T](valueSchemaUsedInRuntime, kafkaConfig, keySchemaDataUsedInRuntime, valueClassTagOpt, keyClassTagOpt)

    if (returnGenericAvroType) {
      new KafkaSource(
        List(preparedTopic),
        kafkaConfig,
        deserializationSchema,
        assignerToUse(kafkaConfig),
        createRecordFormatter
      ) with ReturningType {
        override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(valueSchemaData.schema)

        override val contextInitializer: FlinkContextInitializer[T] = customContextInitializer
      }
    } else {
      new KafkaSource(
        List(preparedTopic),
        kafkaConfig,
        deserializationSchema,
        assignerToUse(kafkaConfig),
        createRecordFormatter
      ) {
        override val contextInitializer: FlinkContextInitializer[T] = customContextInitializer
      }
    }
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampWatermarkHandler[T]] = {
    Some(
      timestampAssigner.getOrElse(
        new LegacyTimestampWatermarkHandler[T](
          new BoundedOutOfOrderPreviousElementAssigner[T](
            kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis)
          )
        )
      )
    )
  }
}
