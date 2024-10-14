package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema
import pl.touk.nussknacker.engine.schemedkafka.{AvroSchemaDeterminer, RuntimeSchemaData, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(
    schemaRegistryClient: SchemaRegistryClient,
    topic: UnspecializedTopicName,
    versionOption: SchemaVersionOption,
    isKey: Boolean
) extends AvroSchemaDeterminer {

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData[AvroSchema]] = {
    val version = versionOption match {
      case ExistingSchemaVersion(v) => Some(v)
      case _                        => None
    }
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err =>
        new SchemaDeterminerError(s"Fetching schema error for topic: ${topic.name}, version: $versionOption", err)
      )
      .andThen(withMetadata =>
        withMetadata.schema match {
          case s: AvroSchema => Valid(RuntimeSchemaData(s.rawSchema(), Some(withMetadata.id)))
          case s =>
            Invalid(
              new SchemaDeterminerError(
                s"Avro schema is required, but got ${s.schemaType()}",
                new IllegalArgumentException("")
              )
            )
        }
      )
  }

}

class ParsedSchemaDeterminer(
    schemaRegistryClient: SchemaRegistryClient,
    topic: UnspecializedTopicName,
    versionOption: SchemaVersionOption,
    isKey: Boolean
) {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData[ParsedSchema]] = {
    versionOption match {
      case ExistingSchemaVersion(v) =>
        val version = Some(v)
        getTypedSchema(version)
      case LatestSchemaVersion =>
        val version = None
        getTypedSchema(version)
      case DynamicSchemaVersion(typ) =>
        getDynamicSchema(typ)
    }

  }

  private def getTypedSchema(
      version: Option[Int]
  ): Validated[SchemaDeterminerError, RuntimeSchemaData[ParsedSchema]] = {
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = isKey)
      .leftMap(err =>
        new SchemaDeterminerError(s"Fetching schema error for topic: ${topic.name}, version: $versionOption", err)
      )
      .map(withMetadata =>
        RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](withMetadata.schema), Some(withMetadata.id))
      )
  }

  private def getDynamicSchema(typ: JsonTypes): Validated[SchemaDeterminerError, RuntimeSchemaData[ParsedSchema]] = {
    typ match {
      case JsonTypes.Json =>
        Valid(
          RuntimeSchemaData[ParsedSchema](
            new NkSerializableParsedSchema[ParsedSchema](
              OpenAPIJsonSchema(
                """{
              |  "anyOf": [{
              |      "type": "object",
              |      "properties": {
              |        "_metadata": {
              |          "oneOf": [
              |            {"type": "null"},
              |            {"type": "object"}
              |          ]
              |        },
              |        "_w": {"type": "boolean"},
              |        "message": {"type": "object"}
              |      }
              |    },
              |    {"type": "object"}]
              |}""".stripMargin
              )
            ),
            Some(SchemaId.fromInt(JsonTypes.Json.value))
          )
        )
      case JsonTypes.Plain =>
        Valid(
          RuntimeSchemaData[ParsedSchema](
            new NkSerializableParsedSchema[ParsedSchema](OpenAPIJsonSchema("""{"type": "string"}""")),
            Some(SchemaId.fromInt(JsonTypes.Plain.value))
          )
        )
      case _ => Invalid(new SchemaDeterminerError("Wrong dynamic type", SchemaError.apply("Wrong dynamic type")))
    }
  }

}
