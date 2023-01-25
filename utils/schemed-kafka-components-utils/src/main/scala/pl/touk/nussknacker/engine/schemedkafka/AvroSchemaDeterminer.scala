package pl.touk.nussknacker.engine.schemedkafka

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.everit.json
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

trait AvroSchemaDeterminer {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData[AvroSchema]]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example:
   * - I have #input of type schemaA
   * - I want to to typed filtering on #input.field1
   * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
   */
  def toRuntimeSchema(schemaUsedInTyping: RuntimeSchemaData[AvroSchema]): Option[RuntimeSchemaData[AvroSchema]] = Some(schemaUsedInTyping)

}

/**
 * This class holds data that will be passed to Flink's tasks for records processing in runtime.
 * @param serializableSchema Serializable Avro schema
 * @param schemaIdOpt optional schema id fetched from schema registry - for further optimizations of record processing in runtime
 */
case class RuntimeSchemaData[T <: ParsedSchema](serializableSchema: NkSerializableParsedSchema[T], schemaIdOpt: Option[SchemaId]) {
  def schema: T = serializableSchema.getParsedSchema

  // Will be better to make T covariant but NkSerializableParsedSchema is java class and it is not supported there
  def toParsedSchemaData: RuntimeSchemaData[ParsedSchema] = this.asInstanceOf[RuntimeSchemaData[ParsedSchema]]
}

object RuntimeSchemaData {
  def apply(schema: Schema, schemaIdOpt: Option[SchemaId]): RuntimeSchemaData[AvroSchema] =
    RuntimeSchemaData(new NkSerializableParsedSchema[AvroSchema](new AvroSchema(schema)), schemaIdOpt)
  def apply(schema: json.schema.Schema, schemaIdOpt: Option[SchemaId]): RuntimeSchemaData[JsonSchema] =
    RuntimeSchemaData(new NkSerializableParsedSchema[JsonSchema](new JsonSchema(schema)), schemaIdOpt)
}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)
