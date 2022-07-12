package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import pl.touk.nussknacker.engine.avro.schemaregistry.{AvroSchema, JsonSchema, SchemaContainer}

trait AvroSchemaDeterminer {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example:
   * - I have #input of type schemaA
   * - I want to to typed filtering on #input.field1
   * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
   */
  def toRuntimeSchema(schemaUsedInTyping: RuntimeSchemaData): Option[RuntimeSchemaData] = Some(schemaUsedInTyping)

}

/**
 * This class holds data that will be passed to Flink's tasks for records processing in runtime.
 * @param serializableSchema Serializable Avro schema
 * @param schemaIdOpt optional schema id fetched from schema registry - for further optimizations of record processing in runtime
 */
//case class RuntimeSchemaData(serializableSchema: NkSerializableAvroSchema, schemaIdOpt: Option[Int]) {
//  def schema: Schema = serializableSchema.getAvroSchema
//}

sealed trait RuntimeSchemaData {
  val schemaIdOpt: Option[Int]

  //this is temporary
  lazy val avroSchema: AvroRuntimeSchemaData = this match {
    case a:AvroRuntimeSchemaData => a
    case JsonRuntimeSchemaData(jsonSchema, schemaIdOpt) => throw new RuntimeException("todo1")
  }
}

case class AvroRuntimeSchemaData(serializableSchema: NkSerializableAvroSchema, schemaIdOpt: Option[Int]) extends RuntimeSchemaData{
  def schema: Schema = serializableSchema.getAvroSchema
}

case class JsonRuntimeSchemaData(jsonSchema: JsonSchema, schemaIdOpt: Option[Int]) extends RuntimeSchemaData


object RuntimeSchemaData {
  def apply(schema: SchemaContainer, schemaIdOpt: Option[Int]): RuntimeSchemaData = {
    schema match {
      case js@JsonSchema(schema) => JsonRuntimeSchemaData(js, schemaIdOpt)
      case AvroSchema(schema) => AvroRuntimeSchemaData(new NkSerializableAvroSchema(schema), schemaIdOpt)
    }
  }
}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)
