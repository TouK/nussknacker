package pl.touk.nussknacker.test

import com.networknt.schema.JsonSchema
import io.circe.Printer
import io.circe.syntax.EncoderOps
import sttp.apispec.circe._
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema

trait NuTapirSchemaTestHelpers {

  protected def prepareJsonSchemaFromTapirSchema[T](schema: sttp.tapir.Schema[T]): JsonSchema = {
    val jsonSchema = TapirSchemaToJsonSchema(
      schema,
      markOptionsAsNullable = true
    ).asJson
    val schemaStr = Printer.spaces2.print(jsonSchema.deepDropNullValues)

    TapirJsonSchemaFactory.instance.getSchema(schemaStr)
  }

}
