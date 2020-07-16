package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroDeserializationSchemaFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.timestamp.BounedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[T: ClassTag](timestampAssigner: Option[TimestampAssigner[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  def createSource(preparedTopic: PreparedKafkaTopic,
                   kafkaConfig: KafkaConfig,
                   deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                   createRecordFormatter: String => Option[RecordFormatter],
                   schemaDeterminer: AvroSchemaDeterminer)
                  (implicit processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] with ReturningType = {

    val schema = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException)
    val returnTypeDefinition = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val schemaUsedInRuntime = schemaDeterminer.toRuntimeSchema(schema)

    new KafkaSource(
      List(preparedTopic),
      kafkaConfig,
      deserializationSchemaFactory.create[T](schemaUsedInRuntime, kafkaConfig),
      assignerToUse(kafkaConfig),
      createRecordFormatter(preparedTopic.prepared),
      TestParsingUtils.newLineSplit
    ) with ReturningType {
      override def returnType: typing.TypingResult = returnTypeDefinition
    }
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampAssigner[T]] = {
    Some(timestampAssigner.getOrElse(
      new BounedOutOfOrderPreviousElementAssigner[T](kafkaConfig.defaultMaxOutOfOrdernessMillis
        .getOrElse(defaultMaxOutOfOrdernessMillis))
    ))
  }
}
