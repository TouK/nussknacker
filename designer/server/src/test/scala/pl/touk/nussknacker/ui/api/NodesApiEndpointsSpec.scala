package pl.touk.nussknacker.ui.api

import io.circe.Printer
import io.circe.syntax.EncoderOps
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.{SchemaClient, SchemaLoader}
import org.json.JSONObject
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.apispec.circe._

import java.net.URI
import scala.util.{Success, Try}

class NodesApiEndpointsSpec extends AnyFreeSpecLike {

  "TypingResult schemas shouldn't go out of sync with Codecs" in {
    val schema = prepareSchemaForTypingResult()

    for (_ <- 1 to 10) {
      TypingResultGen.typingResultGen(EnabledTypedFeatures.All).sample match {
        case Some(sample) =>
          val sampleJson        = TypingResult.encoder.apply(sample)
          val sampleStr: String = Printer.spaces2.print(sampleJson.deepDropNullValues)
          val sampleJsonObject  = new JSONObject(sampleStr)

          Try(schema.validate(sampleJsonObject)) shouldBe Success(())

        case None =>
      }
    }
  }

  def prepareSchemaForTypingResult(): Schema = {
    val typingSchema = TypingDtoSchemas.typingResult

    val jsonSchema = TapirSchemaToJsonSchema(
      typingSchema,
      markOptionsAsNullable = true
    ).asJson
    val schemaStr: String = Printer.spaces2.print(jsonSchema.deepDropNullValues)
    val jsonObject        = new JSONObject(schemaStr)

    val schemaLoader = SchemaLoader
      .builder()
      .schemaClient(SchemaClient.classPathAwareClient())
      .registerSchemaByURI(new URI("pl.touk.nussknacker.engine.api.typed.typing.TypingResult"), jsonObject)
      .schemaJson(jsonObject)
      .build()

    schemaLoader
      .load()
      .build()
      .asInstanceOf[Schema]
  }

}
