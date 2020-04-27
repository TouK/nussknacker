package pl.touk.nussknacker.engine.avro

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.fixed.KafkaAvroFixedSchemaProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.{KafkaAvroSchemaRegistryProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka._

class KafkaAvroSourceFactory[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T],
                                                 processObjectDependencies: ProcessObjectDependencies,
                                                 timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory[T](processObjectDependencies, timestampAssigner) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @ParamName(`TopicParamName`)
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @NotBlank
             topic: String): Source[T] with TestDataGenerator =
    createKafkaAvroSource(processMetaData, topic, KafkaAvroSchemaRegistryProvider(schemaRegistryProvider, kafkaConfig, topic))
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
             @ParamName(`TopicParamName`)
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @NotBlank
             topic: String,
             @ParamName("schema")
             @NotBlank
             stringSchema: String): Source[T] with TestDataGenerator =
    createKafkaAvroSource(
      processMetaData,
      topic,
      new KafkaAvroFixedSchemaProvider(topic, stringSchema, kafkaConfig, formatKey, useSpecificAvroReader)
    )
}

object FixedKafkaAvroSourceFactory {
  def apply[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies): FixedKafkaAvroSourceFactory[T] =
    new FixedKafkaAvroSourceFactory(processObjectDependencies, formatKey = false, useSpecificAvroReader = false, timestampAssigner = None)
}

abstract class BaseKafkaAvroSourceFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaSourceFactory(timestampAssigner, TestParsingUtils.newLineSplit, processObjectDependencies) {

  def createKafkaAvroSource(processMetaData: MetaData, topic: String, kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[T]): KafkaSource =
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      List(topic),
      kafkaAvroSchemaProvider.deserializationSchema,
      kafkaAvroSchemaProvider.recordFormatter,
      processObjectDependencies
    ) with ReturningType {
      override def returnType: typing.TypingResult = kafkaAvroSchemaProvider.typeDefinition
    }
}

