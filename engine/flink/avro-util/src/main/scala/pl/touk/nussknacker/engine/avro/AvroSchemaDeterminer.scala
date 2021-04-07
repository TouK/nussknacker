package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema

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
case class RuntimeSchemaData(serializableSchema: NkSerializableAvroSchema, schemaIdOpt: Option[Int]) {
  def schema: Schema = serializableSchema.getAvroSchema
}

object RuntimeSchemaData {
  def apply(schema: Schema, schemaIdOpt: Option[Int]): RuntimeSchemaData =
    RuntimeSchemaData(new NkSerializableAvroSchema(schema), schemaIdOpt)
}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)

case object FixedNoneSchemaDeterminer extends AvroSchemaDeterminer {
  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, RuntimeSchemaData] = Valid(RuntimeSchemaData(Schema.create(Schema.Type.NULL), None))
  override def toRuntimeSchema(schemaUsedInTyping: RuntimeSchemaData): Option[RuntimeSchemaData] = None
}