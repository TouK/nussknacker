package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.microsoft.azure.schemaregistry.kafka.avro.{KafkaAvroDeserializer, KafkaAvroSerializer}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.IndexedRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, Serializer, StringDeserializer}
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryError}
import pl.touk.nussknacker.engine.schemedkafka.serialization.{KafkaSchemaBasedDeserializationSchemaFactory, KafkaSchemaBasedKeyValueDeserializationSchemaFactory, KafkaSchemaBasedSerializationSchemaFactory, KafkaSchemaBasedValueSerializationSchemaFactory}

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

// TODO: make it universal (handling both avro and json schema, key in avro or string)
class AzureSchemaBasedSerdeProvider extends SchemaBasedSerdeProvider {
  override def serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory =
    new KafkaSchemaBasedValueSerializationSchemaFactory {
      // TODO: handle schemaOpt
      override protected def createValueSerializer(schemaOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Serializer[Any] = {
        val serializer = new KafkaAvroSerializer[Any]
        val adjustedConfig = adjustConfig(kafkaConfig)
        serializer.configure(adjustedConfig.asJava, false)
        serializer
      }
    }

  override def deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory =
    new KafkaSchemaBasedKeyValueDeserializationSchemaFactory {
      // TODO: handle schemaDataOpt
      protected def createDeserializer[T: ClassTag](kafkaConfig: KafkaConfig,
                                                    schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                    isKey: Boolean): Deserializer[T] = {
        val adjustedConfig = adjustConfig(kafkaConfig)
        val deserializer = new KafkaAvroDeserializer[IndexedRecord]()
        deserializer.configure(adjustedConfig.asJava, isKey)
        deserializer.asInstanceOf[Deserializer[T@unchecked]]
      }

      override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K] =
        new StringDeserializer().asInstanceOf[Deserializer[K@unchecked]]

      override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V] =
        createDeserializer[V](kafkaConfig, schemaDataOpt, isKey = false)
    }

  private def adjustConfig(kafkaConfig: KafkaConfig) = {
    val configMap = kafkaConfig.kafkaProperties.getOrElse(Map.empty)
    val configMapWithCredentials = enrichWithCredential(configMap)
    configMapWithCredentials.map {
      case (key@"auto.register.schemas", stringValue: String) => key -> java.lang.Boolean.parseBoolean(stringValue)
      case (key, value) => key -> value
    }
  }

  private def enrichWithCredential(configMap: Map[String, String]) = {
    val configuration = AzureConfigurationFactory.createFromKafkaProperties(configMap)
    val credential = AzureTokenCredentialFactory.createCredential(configuration)
    configMap + ("schema.registry.credential" -> credential)
  }

  // TODO: add testing support
  override def recordFormatterFactory: RecordFormatterFactory = new RecordFormatterFactory {
    override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = new RecordFormatter {
      override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): TestRecord = ???

      override def parseRecord(topic: String, testRecord: TestRecord): ConsumerRecord[Array[Byte], Array[Byte]] = ???
    }
  }

  override def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T] = {
    schema match {
      case s: AvroSchema => Valid(schema)
      case schema => throw new IllegalArgumentException(s"Unsupported schema type: $schema")
    }
  }

}
