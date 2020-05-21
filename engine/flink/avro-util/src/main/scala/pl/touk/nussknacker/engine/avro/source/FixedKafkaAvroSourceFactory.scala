package pl.touk.nussknacker.engine.avro.source

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.fixed.FixedKafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory._
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class FixedKafkaAvroSourceFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies,
                                                      formatKey: Boolean,
                                                      useSpecificAvroReader: Boolean,
                                                      timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory[T](processObjectDependencies, timestampAssigner) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String,
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             //TODO: Create BE and FE validator for verify avro type
             //TODO: Create Avro Editor
             @ParamName(`FixedSchemaParamName`) @NotBlank avroSchemaString: String
            )(implicit nodeId: NodeId): Source[T] with TestDataGenerator with ReturningType = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val kafkaAvroSchemaProvider = FixedKafkaAvroSchemaProvider(topic, avroSchemaString, kafkaConfig, formatKey, useSpecificAvroReader)
    createKafkaAvroSource(topic, kafkaConfig, kafkaAvroSchemaProvider , processMetaData, nodeId)
  }
}

object FixedKafkaAvroSourceFactory {
  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): FixedKafkaAvroSourceFactory[T] =
    new FixedKafkaAvroSourceFactory(processObjectDependencies, formatKey = false, useSpecificAvroReader = false, timestampAssigner = None)
}
