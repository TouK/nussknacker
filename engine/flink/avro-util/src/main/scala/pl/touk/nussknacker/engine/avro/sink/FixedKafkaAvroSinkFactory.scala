package pl.touk.nussknacker.engine.avro.sink

import javax.validation.constraints.NotBlank
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, ParamName}
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory._
import pl.touk.nussknacker.engine.avro.fixed.FixedKafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class FixedKafkaAvroSinkFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory(processObjectDependencies) {

  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String,
             @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR)
             //TODO: Create BE and FE validator for verify avro type
             //TODO: Create Avro Editor
             @ParamName(`FixedSchemaParamName`) @NotBlank avroSchemaString: String,
             @ParamName(`SinkOutputParamName`) @NotBlank output: LazyParameter[GenericContainer]
            )(implicit nodeId: NodeId): Sink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val kafkaAvroSchemaProvider = FixedKafkaAvroSchemaProvider(topic, avroSchemaString, kafkaConfig)
    createSink(topic, output, kafkaConfig, kafkaAvroSchemaProvider, processMetaData, nodeId)
  }
}
