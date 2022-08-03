package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.Schema

import java.util

object AvroSchemaWithJsonPayload {
  val TYPE = "AVRO_JSON_PAYLOAD"
}

case class AvroSchemaWithJsonPayload(avroSchema: AvroSchema) extends ParsedSchema {
  override def schemaType(): String = AvroSchemaWithJsonPayload.TYPE

  override def name(): String = avroSchema.name()

  override def canonicalString(): String = avroSchema.canonicalString()

  override def references(): util.List[SchemaReference] = avroSchema.references()

  override def isBackwardCompatible(previousSchema: ParsedSchema): util.List[String] = avroSchema.isBackwardCompatible(previousSchema)

  override def rawSchema(): Schema = avroSchema.rawSchema()
}
