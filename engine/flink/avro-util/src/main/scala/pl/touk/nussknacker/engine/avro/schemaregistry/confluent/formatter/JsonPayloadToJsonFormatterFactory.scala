package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter

import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
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

  override protected def createKeyTypeInfo[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[K] =
    TypeInformation.of(classOf[Json]).asInstanceOf[TypeInformation[K]]

  override protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): Deserializer[V] =
    toJsonDeserializer.asInstanceOf[Deserializer[V]]

  override protected def createValueTypeInfo[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData], kafkaConfig: KafkaConfig): TypeInformation[V] =
    TypeInformation.of(classOf[Json]).asInstanceOf[TypeInformation[V]]

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
