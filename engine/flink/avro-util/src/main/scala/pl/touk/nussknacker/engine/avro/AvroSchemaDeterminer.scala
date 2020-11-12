package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema

trait AvroSchemaDeterminer {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, SchemaWithId]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example:
   * - I have #input of type schemaA
   * - I want to to typed filtering on #input.field1
   * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
   */
  def toRuntimeSchema(schemaUsedInTyping: SchemaWithId): Option[SchemaWithId] = Some(schemaUsedInTyping)

}

case class SchemaWithId(serializableSchema: NkSerializableAvroSchema, idOpt: Option[Int]) {
  def schema: Schema = serializableSchema.getAvroSchema
}

object SchemaWithId {
  def apply(schema: Schema, idOpt: Option[Int]): SchemaWithId =
    SchemaWithId(new NkSerializableAvroSchema(schema), idOpt)
}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)
