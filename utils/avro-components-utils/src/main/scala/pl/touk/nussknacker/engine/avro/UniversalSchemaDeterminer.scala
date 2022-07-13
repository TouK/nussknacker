package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import io.confluent.kafka.schemaregistry.ParsedSchema

trait UniversalSchemaDeterminer {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, UniversalRuntimeSchemaData]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example:
   * - I have #input of type schemaA
   * - I want to to typed filtering on #input.field1
   * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
   */
  def toRuntimeSchema(schemaUsedInTyping: UniversalRuntimeSchemaData): Option[UniversalRuntimeSchemaData] = Some(schemaUsedInTyping)

}

// todo: write similar implementation to NkSerializableAvroSchema
trait SerializableParsedSchema {
  def getParsedSchema: ParsedSchema
}

object UniversalRuntimeSchemaData {
  def apply(parsedSchema: ParsedSchema, schemaIdOpt: Option[Int]): UniversalRuntimeSchemaData =
    UniversalRuntimeSchemaData(
      //todo: change to real impl
      new SerializableParsedSchema {
        override def getParsedSchema: ParsedSchema = parsedSchema
      }, schemaIdOpt)
}

case class UniversalRuntimeSchemaData(serializableSchema: SerializableParsedSchema, schemaIdOpt: Option[Int]) {
  def schema: ParsedSchema = serializableSchema.getParsedSchema
}


