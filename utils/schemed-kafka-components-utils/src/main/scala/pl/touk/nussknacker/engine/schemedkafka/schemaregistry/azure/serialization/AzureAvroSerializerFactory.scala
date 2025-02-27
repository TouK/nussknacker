package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.serialization

import com.microsoft.azure.schemaregistry.kafka.avro.KafkaAvroSerializer
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericContainer
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.DelegatingKafkaSerializer
import pl.touk.nussknacker.engine.schemedkafka.schema.DefaultAvroSchemaEvolution
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.{AzureSchemaRegistryClient, AzureUtils}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.AzureConfigurationFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.DefaultConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.ConfluentJsonPayloadKafkaSerializer

import java.io.OutputStream
import scala.jdk.CollectionConverters._

object AzureAvroSerializerFactory {

  def createSerializer(
      schemaRegistryClient: AzureSchemaRegistryClient,
      kafkaConfig: KafkaConfig,
      avroSchemaOpt: Option[AvroSchema],
      isKey: Boolean
  ): Serializer[Any] = {
    if (kafkaConfig.avroAsJsonSerialization.contains(true)) {
      createJsonPayloadSerializer(schemaRegistryClient, kafkaConfig, avroSchemaOpt, isKey)
    } else {
      createAvroPayloadSerializer(kafkaConfig, avroSchemaOpt, isKey)
    }
  }

  // For json payload we use Confluent version with some modifications. There is a risk that it won't be compatible
  // with Azure's implementation but on the other hand, it is impossible to pass own Encoder to Azure serializer
  // without copying almost all classes. Encoder is created in com.azure.data.schemaregistry.apacheavro.AvroSerializer
  private def createJsonPayloadSerializer(
      schemaRegistryClient: AzureSchemaRegistryClient,
      kafkaConfig: KafkaConfig,
      avroSchemaOpt: Option[AvroSchema],
      isKey: Boolean
  ) = {
    val confluentClient = new DefaultConfluentSchemaRegistryClient(null)
    new ConfluentJsonPayloadKafkaSerializer(
      kafkaConfig,
      confluentClient,
      new DefaultAvroSchemaEvolution,
      avroSchemaOpt,
      isKey = isKey
    ) {
      override protected def autoRegisterSchemaIfNeeded(
          topic: String,
          data: Any,
          isKey: Boolean,
          avroSchema: AvroSchema
      ): SchemaId = {
        schemaRegistryClient.getSchemaIdByContent(avroSchema)
      }
      override protected def writeHeader(schemaId: SchemaId, out: OutputStream, headers: Headers): Unit = {
        if (!this.isKey) {
          headers.add(AzureUtils.avroContentTypeHeader(schemaId))
        }
      }
    }
  }

  private def createAvroPayloadSerializer(
      kafkaConfig: KafkaConfig,
      avroSchemaOpt: Option[AvroSchema],
      isKey: Boolean
  ) = {
    val azureSerializer = new KafkaAvroSerializer[Any]
    val serializer = avroSchemaOpt
      .map { schema =>
        new DelegatingKafkaSerializer[Any](azureSerializer) {
          val schemaEvolution = new DefaultAvroSchemaEvolution

          override protected def preprocessData(data: Any, topic: String, headers: Headers): Any = {
            data match {
              case generic: GenericContainer => schemaEvolution.alignRecordToSchema(generic, schema.rawSchema())
              case other                     => other
            }
          }
        }
      } getOrElse azureSerializer
    val adjustedConfig = AzureConfigurationFactory.adjustConfig(kafkaConfig.kafkaProperties.getOrElse(Map.empty))
    serializer.configure(adjustedConfig.asJava, false)
    serializer
  }

}
