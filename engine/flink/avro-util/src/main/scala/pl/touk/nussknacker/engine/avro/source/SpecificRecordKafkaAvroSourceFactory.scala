package pl.touk.nussknacker.engine.avro.source

import javax.validation.constraints.NotBlank
import org.apache.avro.specific.SpecificRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.TopicParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryProvider, SpecificRecordEmbeddedSchemaDeterminer}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.source.KafkaSource
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import scala.reflect._

/**
 * Source factory for specific records - mainly generated from schema.
 */
class SpecificRecordKafkaAvroSourceFactory[T <: SpecificRecord: ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                                          processObjectDependencies: ProcessObjectDependencies, timestampAssigner: Option[TimestampWatermarkHandler[T]])
  extends BaseKafkaAvroSourceFactory[T](timestampAssigner) {

  // TODO: it should return suggestions for topics like it is in generic version (KafkaAvroSourceFactory)
  @MethodToInvoke
  def createSource(@ParamName(`TopicParamName`) @NotBlank @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR) topic: String)
                  (implicit processMetaData: MetaData, nodeId: NodeId): KafkaSource[T] = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)
    val valueSchemaDeterminer = new SpecificRecordEmbeddedSchemaDeterminer(classTag[T].runtimeClass.asInstanceOf[Class[_ <: SpecificRecord]])
    createSource(
      preparedTopic,
      kafkaConfig,
      schemaRegistryProvider.deserializationSchemaFactory,
      schemaRegistryProvider.recordFormatter,
      valueSchemaDeterminer,
      keySchemaDeterminer = null,
      returnGenericAvroType = false,
      valueClassTagOpt = Some(classTag[T]),
      keyClassTagOpt = None
    )
  }

}