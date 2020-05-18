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

class KafkaAvroSourceFactory[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T],
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
             @ParamName(`VersionParamName`) @Min(value = 1) @Nullable version: Integer
              )(implicit nodeId: NodeId): Source[T] with TestDataGenerator with ReturningType = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createKafkaAvroSource(topic, kafkaConfig, SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, version), processMetaData, nodeId)
  }
}

object KafkaAvroSourceFactory {
  def apply[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T], processObjectDependencies: ProcessObjectDependencies): KafkaAvroSourceFactory[T] =
    new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
}
