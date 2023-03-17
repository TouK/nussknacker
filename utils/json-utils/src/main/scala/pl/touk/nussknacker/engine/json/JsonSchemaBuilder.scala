package pl.touk.nussknacker.engine.json

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import pl.touk.nussknacker.engine.util.ResourceLoader

object JsonSchemaBuilder {

  def parseSchema(rawJsonSchema: String): Schema =
    parseSchemaAs(rawJsonSchema, useDefaults = true)

  //TODO: parsing schema should be consistent with [io.confluent.kafka.schemaregistry.json.JsonSchema.rawSchema]
  //TODO: Remove strict setting DRAFT7
  def parseSchemaAs[T <: Schema](rawJsonSchema: String, useDefaults: Boolean): T = {
    val trimmedRawSchema = rawJsonSchema.trim
    val rawSchema: Object = if (trimmedRawSchema != "true") {
      new JSONObject(rawJsonSchema)
    } else {
      true.asInstanceOf[Object]
    }

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

  def loadSchemaFromResource(path: String): Schema =
    parseSchema(ResourceLoader.load(path))

}
