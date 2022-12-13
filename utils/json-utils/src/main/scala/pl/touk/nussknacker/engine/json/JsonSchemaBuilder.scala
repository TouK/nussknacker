package pl.touk.nussknacker.engine.json

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

object JsonSchemaBuilder {

  def parseSchema(rawJsonSchema: String): Schema =
    parseSchemaAs(rawJsonSchema, useDefaults = true)

  //TODO: parsing schema should be consistent with [io.confluent.kafka.schemaregistry.json.JsonSchema.rawSchema]
  //TODO: Remove strict setting DRAFT7
  def parseSchemaAs[T <: Schema](rawJsonSchema: String, useDefaults: Boolean): T = {
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
