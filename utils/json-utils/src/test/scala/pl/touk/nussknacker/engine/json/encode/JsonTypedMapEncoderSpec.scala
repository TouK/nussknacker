package pl.touk.nussknacker.engine.json.encode

import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonObject}
import org.everit.json.schema.Schema
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger.SwaggerObject
import pl.touk.nussknacker.engine.json.{JsonSchemaBuilder, SwaggerBasedJsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonTypedMap
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

class JsonTypedMapEncoderSpec extends AnyFunSuite {

  val lazyMapToJsonEncoder: PartialFunction[Any, Json] =
    new LazyJsonTypedMapEncoder().encoder(BestEffortJsonEncoder.defaultForTests.encode)

  test("should encode JsonLazyMap") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{
                                                         |  "type": "object",
                                                         |  "properties": {
                                                         |    "firstName": {
                                                         |      "type": "string"
                                                         |    },
                                                         |    "lastName": {
                                                         |      "type": "string"
                                                         |    },
                                                         |    "age": {
                                                         |      "type": "integer"
                                                         |    }
                                                         |  },
                                                         |}""".stripMargin)

    val jsonObject = JsonObject(
      "firstName" -> Json.fromString("John"),
      "lastName"  -> Json.fromString("Smith"),
      "age"       -> Json.fromLong(1),
    )

    val swaggerObject = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).asInstanceOf[SwaggerObject]
    val lazyMap = JsonTypedMap(
      jsonObject,
      swaggerObject
    )

    lazyMap shouldBe TypedMap(
      Map(
        "firstName" -> "John",
        "lastName"  -> "Smith",
        "age"       -> 1L
      )
    )
    lazyMapToJsonEncoder(lazyMap) shouldEqual jsonObject.asJson

  }

}
