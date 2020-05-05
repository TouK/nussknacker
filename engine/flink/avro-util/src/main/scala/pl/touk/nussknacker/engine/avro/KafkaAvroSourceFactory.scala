package pl.touk.nussknacker.engine.avro

import javax.validation.constraints.NotBlank
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.formatter.AvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.DeserializationSchemaFactory

class KafkaAvroSourceFactory[T: TypeInformation](schemaFactory: DeserializationSchemaFactory[T],
                                                 schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                 timestampAssigner: Option[TimestampAssigner[T]],
                                                 formatKey: Boolean = false,
                                                 processObjectDependencies: ProcessObjectDependencies)
  extends KafkaSourceFactory[T](schemaFactory, timestampAssigner,
    TestParsingUtils.newLineSplit, processObjectDependencies) {

  override protected def createSource(topics: List[String],
                                      kafkaConfig: KafkaConfig,
                                      schema: KafkaDeserializationSchema[T]): KafkaSource = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new KafkaSource(topics, kafkaConfig, schema,
      Some(AvroToJsonFormatter(schemaRegistryClient, topics.head, formatKey)), processObjectDependencies)
  }

}

class KafkaTypedAvroSourceFactory[T: TypeInformation](schemaFactory: DeserializationSchemaFactory[T],
                                                      schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                      timestampAssigner: Option[TimestampAssigner[T]],
                                                      formatKey: Boolean = false,
                                                      processObjectDependencies: ProcessObjectDependencies)
  extends BaseKafkaSourceFactory[T](timestampAssigner, TestParsingUtils.newLineSplit, processObjectDependencies) {

  @MethodToInvoke
  def create(@ParamName(`TopicParamName`)
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
    val kafkaConfig = KafkaSourceFactory.parseKafkaConfig(processObjectDependencies)
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(kafkaConfig)
    new KafkaSource(List(topic), kafkaConfig, schemaFactory.create(List(topic), kafkaConfig),
      Some(AvroToJsonFormatter(schemaRegistryClient, topic, formatKey)), processObjectDependencies) with ReturningType {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(avroSchema)
    }
  }

}
