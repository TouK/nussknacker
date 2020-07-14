package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema

trait AvroSchemaDeterminer extends Serializable {

  def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, Schema]

  /**
   * This method should provide schema that will be used in runtime. In some cases it can be other than schema used
   * in typing. For example when you want to use schema of record that will be consumed in source and have a typing
   * just as a addition that have little influence on processing. See `UsingRecordSchemaInRuntimeAvroSchemaDeterminer`
   * for more info.
   */
  def determineSchemaInRuntime: Validated[SchemaDeterminerError, Option[Schema]]
}

class SchemaDeterminerError(message: String, cause: Throwable) extends RuntimeException(message, cause)
