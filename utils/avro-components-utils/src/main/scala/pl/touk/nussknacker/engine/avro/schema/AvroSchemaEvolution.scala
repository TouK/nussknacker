package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer

trait AvroSchemaEvolution {
  def alignRecordToSchema(record: GenericContainer, schema: Schema): Any
  def canBeEvolved(record: GenericContainer, schema: Schema): Boolean
}

class AvroSchemaEvolutionException(message: String, cause: Throwable) extends RuntimeException(message, cause)
