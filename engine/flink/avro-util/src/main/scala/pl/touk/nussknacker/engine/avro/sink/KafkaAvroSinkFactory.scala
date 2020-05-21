package pl.touk.nussknacker.engine.avro.sink

import javax.annotation.Nullable
import javax.validation.constraints.{Min, NotBlank}
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory._
import pl.touk.nussknacker.engine.avro.schemaregistry._
import pl.touk.nussknacker.engine.kafka.KafkaConfig

class KafkaAvroSinkFactory(schemaRegistryProvider: SchemaRegistryProvider[_], processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaAvroSinkFactory(processObjectDependencies) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String,
             @ParamName(SchemaVersionParamName) @Nullable @Min(value = 1) version: Integer,
             @ParamName(`SinkOutputParamName`) @NotBlank output: LazyParameter[GenericContainer]
            )(implicit nodeId: NodeId): Sink = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val kafkaAvroSchemaProvider = SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, version)
    createSink(topic, output, kafkaConfig, kafkaAvroSchemaProvider, processMetaData, nodeId)
  }
}
