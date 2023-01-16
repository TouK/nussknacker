package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.serialization

import com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroSerializer
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericContainer
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.DelegatingKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.AzureConfigurationFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.SchemaRegistryBasedSerializerFactory

import scala.jdk.CollectionConverters._

object AzureAvroSerializerFactory extends SchemaRegistryBasedSerializerFactory {

  override def createSerializer(schemaRegistryClient: SchemaRegistryClient,
                                kafkaConfig: KafkaConfig,
                                schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                isKey: Boolean): Serializer[Any] = {
    val avroSchemaOpt = schemaDataOpt
      .map(_.schema.asInstanceOf[AvroSchema])
    createSerializer(kafkaConfig, avroSchemaOpt)
  }

  def createSerializer(kafkaConfig: KafkaConfig, avroSchemaOpt: Option[AvroSchema]): Serializer[Any] = {
    val azureSerializer = new KafkaAvroSerializer[Any]
    val serializer = avroSchemaOpt
      .map { schema =>
        new DelegatingKafkaSerializer[Any](azureSerializer) {
          val schemaEvolution = new DefaultAvroSchemaEvolution

          override protected def preprocessData(data: Any, topic: String, headers: Headers): Any = {
            data match {
              case generic: GenericContainer => schemaEvolution.alignRecordToSchema(generic, schema.rawSchema())
              case other => other
            }
          }
        }
      } getOrElse azureSerializer
    val adjustedConfig = AzureConfigurationFactory.adjustConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty))
    serializer.configure(adjustedConfig.asJava, false)
    serializer
  }
}
