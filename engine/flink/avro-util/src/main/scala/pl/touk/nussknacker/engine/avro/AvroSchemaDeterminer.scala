package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema

trait AvroSchemaDeterminer extends Serializable {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, Schema]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example:
   * - I have #input of type schemaA
   * - I want to to typed filtering on #input.field1
   * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
   */
  def toRuntimeSchema(schemaUsedInTyping: Schema): Option[Schema] = Some(schemaUsedInTyping)

}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)
