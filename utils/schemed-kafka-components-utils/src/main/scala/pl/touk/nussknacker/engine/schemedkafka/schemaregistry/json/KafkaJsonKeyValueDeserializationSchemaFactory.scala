package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.json

import io.circe.Json
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.serialization.KafkaSchemaBasedKeyValueDeserializationSchemaFactory

import java.nio.charset.StandardCharsets

class KafkaJsonKeyValueDeserializationSchemaFactory(override protected val kafkaComponentsConfig: KafkaComponentsConfig)
    extends KafkaSchemaBasedKeyValueDeserializationSchemaFactory {

  override protected def createKeyDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
  ): Deserializer[K] =
    toJsonDeserializer.asInstanceOf[Deserializer[K]]

  override protected def createValueDeserializer[V](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      dataSampleTypingResult: Option[TypingResult]
  ): Deserializer[V] =
    toJsonDeserializer.asInstanceOf[Deserializer[V]]

  // always deserialize key to valid Json
  override protected def createStringKeyDeserializer: Deserializer[_] = (_: String, data: Array[Byte]) =>
    Option(data).map(data => Json.fromString(new String(data, StandardCharsets.UTF_8))).orNull

  private def toJsonDeserializer: Deserializer[Json] = (_: String, data: Array[Byte]) =>
    CirceUtil.decodeJsonUnsafe[Json](data)

}
