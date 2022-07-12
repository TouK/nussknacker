package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroKeyValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatter
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatter, RecordFormatterFactory, serialization}

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

/**
  * RecordFormatter factory for kafka avro sources with json payload.
  *
  * Test data record is a simple record representing data of ConsumerRecord. It does not contain schema ids.
  * Creates instance of ConsumerRecordToJsonFormatter with fixed key-value types (key and value are always deserialized to Json).
  */
class JsonPayloadToJsonFormatterFactory extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    val asJsonDeserializerFactory = new KafkaJsonKeyValueDeserializationSchemaFactory
    val asJsonDeserializer = asJsonDeserializerFactory.create[Json, Json](kafkaConfig, None, None)

    new ConsumerRecordToJsonFormatter[Json, Json](asJsonDeserializer)
  }

}

class KafkaJsonKeyValueDeserializationSchemaFactory extends KafkaAvroKeyValueDeserializationSchemaFactory {

  override protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[K] =
    toJsonDeserializer.asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    toJsonDeserializer.asInstanceOf[Deserializer[V]]

  // always deserialize key to valid Json
  override protected def createStringKeyDeserializer: Deserializer[_] = new Deserializer[Json] {
    override def deserialize(topic: String, data: Array[Byte]): Json =
      Option(data).map(data => Json.fromString(new String(data, StandardCharsets.UTF_8))).orNull
  }

  private def toJsonDeserializer: Deserializer[Json] = new Deserializer[Json] {
    override def deserialize(topic: String, data: Array[Byte]): Json =
      CirceUtil.decodeJsonUnsafe[Json](data)
  }
}


class JsonOrAvroPayloadFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {
    lazy val jsonFormatter = new JsonPayloadToJsonFormatterFactory().create(kafkaConfig, kafkaSourceDeserializationSchema)
    lazy val avroFormatter = new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory).create(kafkaConfig, kafkaSourceDeserializationSchema)
    val srClient = schemaRegistryClientFactory.create(kafkaConfig).client

    def handleSchemaType[T](topic: String)(run: RecordFormatter => T):T  = {
      val subject = ConfluentUtils.topicSubject(topic, isKey = false)
      srClient.getLatestSchemaMetadata(subject).getSchemaType match {
        case "avro" => run(avroFormatter)
        case "json" => run(jsonFormatter)
        case _ => throw new IllegalArgumentException("Unsuported schema type")
      }
    }

    new RecordFormatter {
      override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
        handleSchemaType(record.topic())(_.formatRecord(record))
      }
      override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
        handleSchemaType(topic)(_.parseRecord(topic, bytes))
      }
      override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit
    }
  }

}


class ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends RecordFormatterFactory {

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig, kafkaSourceDeserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]): RecordFormatter = {

    val schemaRegistryClient = schemaRegistryClientFactory.create(kafkaConfig)
    val messageFormatter = new ConfluentAvroMessageFormatter(schemaRegistryClient.client)
    val messageReader = new ConfluentAvroMessageReader(schemaRegistryClient.client)

    new ConfluentAvroToJsonFormatter(kafkaConfig, schemaRegistryClient.client, messageFormatter, messageReader, kafkaSourceDeserializationSchema)
  }

}