package pl.touk.nussknacker.ui.api

import io.circe.Printer
import io.circe.syntax.EncoderOps
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.{SchemaClient, SchemaLoader}
import org.json.JSONObject
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.apispec.circe._
import java.net.URI
import scala.util.{Success, Try}

class NodesApiEndpointsSpec extends AnyFreeSpecLike with ScalaCheckDrivenPropertyChecks with Matchers {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 5, minSize = 0)

  "TypingResult schemas shouldn't go out of sync with Codecs" in {
    val schema = prepareSchemaForTypingResult()

    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures.All)) { typingResult =>
      val json = createJsonObjectFromTypingResult(typingResult)

      schema should validateJson(json)
    }
  }

  def validateJson(json: JSONObject): Matcher[Schema] = (left: Schema) => {
    MatchResult(
      Try(left.validate(json)) == Success(()),
      s"JSON $json cannot be validated by schema $left",
      s"JSON $json can be validated by schema $left"
    )

  }

  private def prepareSchemaForTypingResult(): Schema = {
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

  private def createJsonObjectFromTypingResult(typingResult: TypingResult): JSONObject = {
    val sampleJson        = TypingResult.encoder.apply(typingResult)
    val sampleStr: String = Printer.spaces2.print(sampleJson.deepDropNullValues)
    new JSONObject(sampleStr)
  }

}
