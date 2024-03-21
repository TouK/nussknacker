package pl.touk.nussknacker.test

import io.circe.{Encoder, Printer}
import io.circe.syntax.EncoderOps
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.apispec.circe._

trait NuTapirExtensions {

  protected def prepareJsonSchemaFromTapirSchema[T](schema: sttp.tapir.Schema[T]): org.everit.json.schema.Schema = {
    val jsonSchema = TapirSchemaToJsonSchema(
      schema,
      markOptionsAsNullable = true
    ).asJson
    val schemaStr: String = Printer.spaces2.print(jsonSchema.deepDropNullValues)
    val jsonObject        = new JSONObject(schemaStr)

    val schemaLoader = SchemaLoader
      .builder()
      .schemaJson(jsonObject)
      .build()

    schemaLoader
      .load()
      .build()
      .asInstanceOf[Schema]
  }

  protected def createJsonObjectFrom[T](value: T)(implicit encoder: Encoder[T]): JSONObject = {
    val sampleJson        = encoder.apply(value)
    val sampleStr: String = Printer.spaces2.print(sampleJson.deepDropNullValues)
    new JSONObject(sampleStr)
  }

  protected def createJsonFromString(json: String): JSONObject = {
    new JSONObject(json)
  }

}
