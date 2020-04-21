package pl.touk.nussknacker.engine.avro

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka._

class KafkaAvroSourceFactory[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T],
                                                 processObjectDependencies: ProcessObjectDependencies)
  extends KafkaSourceFactory[T](
    schemaRegistryProvider.deserializationSchemaFactory,
    schemaRegistryProvider.timestampAssigner,
    TestParsingUtils.newLineSplit,
    processObjectDependencies
  ) {

  override protected def createSource(processMetaData: MetaData, topics: List[String], schema: KafkaDeserializationSchema[T]): KafkaSource = {
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topics,
      schema,
      Some(schemaRegistryProvider.recordFormatter(topics.head, formatKey = false)),
      processObjectDependencies
    )
  }

}

class KafkaTypedAvroSourceFactory[T: TypeInformation](schemaRegistryProvider: SchemaRegistryProvider[T],
                                                      processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory[T](
    schemaRegistryProvider.timestampAssigner,
    TestParsingUtils.newLineSplit,
    processObjectDependencies
  ) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @ParamName(`TopicParamName`)
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @NotBlank
             topic: String,
             // json or avro schema on this level?
             @ParamName("schema")
             @NotBlank
             avroSchema: String): Source[T] with TestDataGenerator = {

    new KafkaSource(
      consumerGroupId = processMetaData.id,
      List(topic),
      schemaRegistryProvider.deserializationSchemaFactory.create(List(topic), kafkaConfig),
      Some(schemaRegistryProvider.recordFormatter(topic, formatKey = false)),
      processObjectDependencies
    ) with ReturningType {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(avroSchema)
    }
  }

}
