package pl.touk.nussknacker.engine.json

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

object JsonSchemaBuilder {

  def parseSchema(rawJsonSchema: String): Schema =
    parseSchema(rawJsonSchema, useDefaults = true, nullableSupport = true)

  def parseSchema(rawJsonSchema: String, useDefaults: Boolean, nullableSupport: Boolean): Schema = {
    val rawSchema: JSONObject = new JSONObject(rawJsonSchema)

    SchemaLoader
      .builder()
      .useDefaults(useDefaults)
      .schemaJson(rawSchema)
      .draftV7Support()
      .build()
      .load()
      .build()
      .asInstanceOf[Schema]
  }

}
