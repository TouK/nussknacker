package pl.touk.nussknacker.engine.avro

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.util.serialization.KeyedDeserializationSchema
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.avro.formatter.AvroToJsonFormatter
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
                                      schema: KeyedDeserializationSchema[T]): KafkaSource = {
    val schemaRegistryClient = schemaRegistryClientFactory.createSchemaRegistryClient(config)
    new KafkaSource(consumerGroupId = processMetaData.id, topics, schema,
      Some(AvroToJsonFormatter(schemaRegistryClient, topics.head, formatKey)))
  }

}