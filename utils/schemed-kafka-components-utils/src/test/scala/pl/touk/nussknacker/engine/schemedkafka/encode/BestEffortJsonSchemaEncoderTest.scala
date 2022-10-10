package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import io.circe.Json
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.encode.BestEffortJsonSchemaEncoder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

class BestEffortJsonSchemaEncoderTest extends AnyFunSuite {

  val encoder = new BestEffortJsonSchemaEncoder(ValidationMode.strict)

  test("should encode object") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
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
        |  }
        |}""".stripMargin))


    val encoded = encoder.encode(Map(
      "firstName" -> "John",
      "lastName" -> "Smith",
      "age" -> 1L
    ), schema)

    encoded shouldEqual Valid(Json.obj(
      "firstName" -> Json.fromString("John"),
      "lastName" -> Json.fromString("Smith"),
      "age" -> Json.fromLong(1),
    ))
  }

  test("should encode string") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string"
        |}""".stripMargin))

    val encoded = encoder.encode("1", schema)

    encoded shouldEqual Valid(Json.fromString("1"))
  }

  test("should encode number") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "number"
        |}""".stripMargin))

    val encoded = encoder.encode(1L, schema)

    encoded shouldEqual Valid(Json.fromLong(1L))
  }

  test("should encode array") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "array",
        |  "items": {
        |    "type": "number"
        |  }
        |}""".stripMargin))

    val encoded = encoder.encode(List(1), schema)

    encoded shouldEqual Valid(Json.arr(Json.fromLong(1L)))
  }

  test("should throw when value and schema type mismatch") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "number"
        |}""".stripMargin))

    val encoded = encoder.encode("1", schema)

    encoded shouldEqual Invalid(NonEmptyList("Not expected type: java.lang.String for field: None with schema: {\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft-07/schema\"}", List()))
  }

  test("should accept redundant parameters if validation modes allows this") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  }
        |}""".stripMargin))

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> "bar", "redundant" -> 15), schema) shouldBe 'valid
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> "bar", "redundant" -> 15), schema) shouldBe 'invalid
  }

  test("should encode not required property with empty map") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string"]
        |    }
        |  }
        |}""".stripMargin))

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map(), schema) shouldBe 'valid
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map(), schema) shouldBe 'valid
  }

  //todo test when union support(CombinedSchema) will be implemented for JsonSchema
  ignore("should encode null value for nullable field") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string", "null"]
        |    }
        |  }
        |}""".stripMargin))

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> null), schema) shouldBe 'valid
  }


}
