package pl.touk.nussknacker.engine.json

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

object JsonSchemaBuilder {

  def parseSchema[T <: Schema](rawJsonSchema: String): T =
    parseSchema(rawJsonSchema, useDefaults = true)

  //TODO: parsing schema should be consistent with [io.confluent.kafka.schemaregistry.json.JsonSchema.rawSchema]
  def parseSchema[T <: Schema](rawJsonSchema: String, useDefaults: Boolean): T = {
    val rawSchema: JSONObject = new JSONObject(rawJsonSchema)

    SchemaLoader
      .builder()
      .useDefaults(useDefaults)
      .schemaJson(rawSchema)
      .draftV7Support()
      .build()
      .load()
      .build()
      .asInstanceOf[T]
  }

}
