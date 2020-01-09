package pl.touk.nussknacker.engine.avro

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.formatter.AvroToJsonFormatter
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory._
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.DeserializationSchemaFactory

class KafkaAvroSourceFactory[T: TypeInformation](config: KafkaConfig,
                                                 schemaFactory: DeserializationSchemaFactory[T],
                                                 schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                 timestampAssigner: Option[TimestampAssigner[T]],
                                                 formatKey: Boolean = false)
  extends KafkaSourceFactory[T](config, schemaFactory, timestampAssigner, TestParsingUtils.newLineSplit) {

  override protected def createSource(processMetaData: MetaData,
                                      topics: List[String],
                                      schema: KafkaDeserializationSchema[T]): KafkaSource = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(config)
    new KafkaSource(consumerGroupId = processMetaData.id, topics, schema,
      Some(AvroToJsonFormatter(schemaRegistryClient, topics.head, formatKey)))
  }

}

class KafkaTypedAvroSourceFactory[T: TypeInformation](config: KafkaConfig,
                                                      schemaFactory: DeserializationSchemaFactory[T],
                                                      schemaRegistryClientFactory: SchemaRegistryClientFactory,
                                                      timestampAssigner: Option[TimestampAssigner[T]],
                                                      formatKey: Boolean = false)
  extends BaseKafkaSourceFactory[T](config, timestampAssigner, TestParsingUtils.newLineSplit) {

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @ParamName(`TopicParamName`) topic: String,
             // json or avro schema on this level?
             @ParamName("schema") avroSchema: String): Source[T] with TestDataGenerator = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(config)
    new KafkaSource(consumerGroupId = processMetaData.id, List(topic), schemaFactory.create(List(topic), config),
      Some(AvroToJsonFormatter(schemaRegistryClient, topic, formatKey))) with ReturningType {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(avroSchema)
    }
  }

}