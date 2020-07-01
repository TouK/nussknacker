package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

/**
  * FromSubjectVersion - Deserializer always fetches schema by given subject and schema version as parameters
  * FromRecord - Standard Confluent Deserializer, it always fetches schema by message schema id
  */
object SchemaDeterminingStrategy extends Enumeration {
  type SchemaDeterminingStrategy = Value
  val FromRecord, FromSubjectVersion = Value
}
