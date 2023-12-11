package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.json

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

class KafkaJsonKeyValueDeserializationSchemaFactory extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory {

  override protected def createKeyDeserializer[K: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K] =
    toJsonDeserializer.asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer[V: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[V] =
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
