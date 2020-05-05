package pl.touk.nussknacker.engine.avro

import cats.data.Validated.{Invalid, Valid}
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.fixed.FixedKafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryKafkaAvroProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka._

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
             @ParamName("Schema version") @Nullable version: Integer
              )(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    createKafkaAvroSource(topic, kafkaConfig, SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topic, version), processMetaData, nodeId)
  }
}

object KafkaAvroSourceFactory {
  def apply[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T], processObjectDependencies: ProcessObjectDependencies): KafkaAvroSourceFactory[T] =
    new KafkaAvroSourceFactory(schemaRegistryProvider, processObjectDependencies, None)
}

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
             @ParamName("schema") @NotBlank avroSchema: String
            )(implicit nodeId: NodeId): Source[T] with TestDataGenerator = {
    val kafkaConfig = KafkaConfig.parseConfig(processObjectDependencies.config, "kafka")
    createKafkaAvroSource(
      topic,
      kafkaConfig,
      new FixedKafkaAvroSchemaProvider(topic, avroSchema, kafkaConfig, formatKey, useSpecificAvroReader),
      processMetaData, nodeId)
  }

}

object FixedKafkaAvroSourceFactory {
  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): FixedKafkaAvroSourceFactory[T] =
    new FixedKafkaAvroSourceFactory(processObjectDependencies, formatKey = false, useSpecificAvroReader = false, timestampAssigner = None)
}

abstract class BaseKafkaAvroSourceFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaSourceFactory(timestampAssigner, TestParsingUtils.newLineSplit, processObjectDependencies) {

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  def createKafkaAvroSource(topic: String,
                            kafkaConfig: KafkaConfig,
                            kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[T],
                            processMetaData: MetaData,
                            nodeId: NodeId): KafkaSource = {
    val returnTypeDefinition = kafkaAvroSchemaProvider.returnType

    new KafkaSource(
      List(topic),
      kafkaConfig,
      kafkaAvroSchemaProvider.deserializationSchema,
      kafkaAvroSchemaProvider.recordFormatter,
      processObjectDependencies
    ) with ReturningType {
      override def returnType: typing.TypingResult = returnTypeDefinition
    }
  }
}
