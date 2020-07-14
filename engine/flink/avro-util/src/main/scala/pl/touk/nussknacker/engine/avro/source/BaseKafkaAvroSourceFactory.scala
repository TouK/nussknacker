package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerErrorHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.timestamp.BounedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[T: ClassTag](processObjectDependencies: ProcessObjectDependencies,
                                                       timestampAssigner: Option[TimestampAssigner[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  def createSource(preparedTopic: PreparedKafkaTopic,
                   version: Option[Int],
                   kafkaConfig: KafkaConfig,
                   deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T],
                   createRecordFormatter: String => Option[RecordFormatter],
                   schemaDeterminer: AvroSchemaDeterminer,
                   processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] with ReturningType = {

    val schema = schemaDeterminer.determineSchemaUsedInTyping.valueOr(SchemaDeterminerErrorHandler.handleSchemaRegistryErrorAndThrowException(_)(nodeId))
    val returnTypeDefinition = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)

    new KafkaSource(
      List(preparedTopic),
      kafkaConfig,
      deserializationSchemaFactory.create(List(preparedTopic.prepared), version, kafkaConfig),
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
