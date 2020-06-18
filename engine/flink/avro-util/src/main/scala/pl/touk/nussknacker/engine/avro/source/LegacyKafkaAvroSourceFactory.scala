package pl.touk.nussknacker.engine.avro.source

import javax.annotation.Nullable
import javax.validation.constraints.{Min, NotBlank}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryKafkaAvroProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory._

class LegacyKafkaAvroSourceFactory[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T],
                                                       processObjectDependencies: ProcessObjectDependencies,
                                                       timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory[T](processObjectDependencies, timestampAssigner) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @ParamName(`TopicParamName`) @NotBlank topic: String,
             @ParamName(`SchemaVersionParamName`) @Min(value = 1) @Nullable version: Integer
              )(implicit nodeId: NodeId): Source[T] with TestDataGenerator with ReturningType = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val kafkaAvroSchemaProvider = SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, version)
    createSource(topic, kafkaConfig, kafkaAvroSchemaProvider, processMetaData, nodeId)
  }
}

object LegacyKafkaAvroSourceFactory {
  def apply[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T], processObjectDependencies: ProcessObjectDependencies): LegacyKafkaAvroSourceFactory[T] =
    new LegacyKafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
}
