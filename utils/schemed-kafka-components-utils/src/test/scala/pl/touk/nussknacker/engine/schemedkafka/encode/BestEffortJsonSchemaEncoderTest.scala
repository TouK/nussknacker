package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.Validated.{Invalid, Valid, valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.circe.Json
import io.circe.Json.{Null, fromLong, fromString, obj}
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.everit.json.schema.Schema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.encode.BestEffortJsonSchemaEncoder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}

class BestEffortJsonSchemaEncoderTest extends AnyFunSuite {

  private val encoderStrict = new BestEffortJsonSchemaEncoder(ValidationMode.strict)
  private val encoderLax = new BestEffortJsonSchemaEncoder(ValidationMode.lax)

  test("should encode object") {
    val schema: Schema = JsonSchemaBuilder.parseSchema(
      """{
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
        |}""".stripMargin)

    val encoded = encoderStrict.encode(Map(
      "firstName" -> "John",
      "lastName" -> "Smith",
      "age" -> 1L
    ), schema)

    encoded shouldEqual Valid(Json.obj(
      "firstName" -> Json.fromString("John"),
      "lastName" -> Json.fromString("Smith"),
      "age" -> Json.fromLong(1),
    ))

    encoderStrict.encode(Map(
      "firstName" -> "John",
      "lastName" -> 1,
      "age" -> 1L
    ), schema) shouldBe Invalid(NonEmptyList.of("""Not expected type: java.lang.Integer for field: 'lastName' with schema: {"type":"string"}"""))
  }

  test("should encode string") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string"}""".stripMargin)
    val encoded = encoderStrict.encode("1", schema)
    encoded shouldEqual Valid(Json.fromString("1"))
  }

  test("should encode date time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date-time"}""".stripMargin)
    val zdt = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    val encodedZdt = encoderStrict.encode(zdt, schema)
    encodedZdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))

    val encodedOdt = encoderStrict.encode(zdt.toOffsetDateTime, schema)
    encodedOdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))
  }

  test("should encode date") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoderStrict.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("2020-07-10"))
  }

  test("should encode time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "time"}""".stripMargin)
    val date = OffsetTime.parse("20:20:39+01:00", DateTimeFormatter.ISO_OFFSET_TIME)
    val encoded = encoderStrict.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("20:20:39+01:00"))
  }

  test("should throw when wrong date-time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date-time"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoderStrict.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong time") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "time"}""".stripMargin)
    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    val encoded = encoderStrict.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong date") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "string","format": "date"}""".stripMargin)
    val date = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    val encoded = encoderStrict.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should encode number") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "number"}""".stripMargin)
    val encoded = encoderStrict.encode(1L, schema)

    encoded shouldEqual Valid(Json.fromLong(1L))
  }

  test("should encode array") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "array","items": {"type": "number"}}""".stripMargin)
    val encoded = encoderStrict.encode(List(1), schema)

    encoded shouldEqual Valid(Json.arr(Json.fromLong(1L)))
  }

  test("should throw when value and schema type mismatch") {
    val schema: Schema = JsonSchemaBuilder.parseSchema("""{"type": "number"}""".stripMargin)
    val encodedLax = new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode("1", schema)
    val encodedStrict = new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode("1", schema)

    encodedLax shouldEqual Invalid(NonEmptyList("Not expected type: java.lang.String for field with schema: {\"type\":\"number\"}", List()))
    encodedStrict shouldEqual Invalid(NonEmptyList("Not expected type: java.lang.String for field with schema: {\"type\":\"number\"}", List()))
  }

  test("should accept and encode redundant parameters if schema allows this") {
    val allowAdditionalProperties: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  }
        |}""".stripMargin)

    val rejectAdditionalProperties: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": false
        |}""".stripMargin)

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> "bar", "redundant" -> 15), allowAdditionalProperties) shouldBe Valid(Json.obj(("foo", Json.fromString("bar")), ("redundant", Json.fromLong(15))))
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> "bar", "redundant" -> 15), allowAdditionalProperties) shouldBe Valid(Json.obj(("foo", Json.fromString("bar")), ("redundant", Json.fromLong(15))))
    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> "bar", "redundant" -> 15), rejectAdditionalProperties) shouldBe 'invalid
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> "bar", "redundant" -> 15), rejectAdditionalProperties) shouldBe 'invalid
  }

  test("should validate additionalParameters type") {
    def schema(additionalPropertiesType: String): Schema = JsonSchemaBuilder.parseSchema(
      s"""{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": {
        |     "type": "$additionalPropertiesType"
        |  }
        |}""".stripMargin)

    encoderLax.encode(Map("foo" -> "bar", "redundant" -> "aaa"), schema("number")) shouldBe
      Invalid(NonEmptyList.of("""Not expected type: java.lang.String for field with schema: {"type":"number"}"""))
    encoderLax.encode(Map("foo" -> "bar", "redundant" -> 15), schema("number")) shouldBe
      Valid(Json.obj(("foo", Json.fromString("bar")), ("redundant", Json.fromLong(15))))
    encoderLax.encode(Map("foo" -> "bar", "redundant" -> 15), schema("string")) shouldBe 'invalid
  }

  test("should encode not required property with empty map") {
    val schema: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string"]
        |    }
        |  }
        |}""".stripMargin)

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> null), schema) shouldBe 'valid
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> null), schema) shouldBe 'valid
  }

  test("should encode null value for nullable field") {
    val schema: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string", "null"]
        |    }
        |  }
        |}""".stripMargin)

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> null), schema) shouldBe 'valid
  }

  test("should encode union") {
    forAll(Table(
      "schema",
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["string", "integer"]
        |    }
        |  }
        |}""".stripMargin,
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "anyOf": [
        |        { "type": "string" },
        |        { "type": "integer" }
        |      ]
        |    }
        |  }
        |}""".stripMargin,
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "oneOf": [
        |        { "type": "string" },
        |        { "type": "integer" }
        |      ]
        |    }
        |  }
        |}""".stripMargin
    )) { schemaString =>
      val schema: Schema = JsonSchemaBuilder.parseSchema(schemaString)

      new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> 1), schema) shouldBe Valid(Json.obj(("foo", Json.fromLong(1L))))
      new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> 1), schema) shouldBe Valid(Json.obj(("foo", Json.fromLong(1L))))

      new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map("foo" -> "1"), schema) shouldBe Valid(Json.obj(("foo", Json.fromString("1"))))
      new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map("foo" -> "1"), schema) shouldBe Valid(Json.obj(("foo", Json.fromString("1"))))
    }
  }

  test("handling encode null value") {
    val objString: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  }
        |}""".stripMargin)

    val objUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["null", "string"]
        |    }
        |  }
        |}""".stripMargin)

    val objUnionNullStringRequired: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": ["null", "string"]
        |    }
        |  },
        |  "required": ["foo"]
        |}""".stripMargin)

    forAll(Table(
      ("data", "schema", "result"),
      (Map(), objString, obj()),
      (Map("foo" -> null), objString, obj()),
      (Map(), objUnionNullString, obj()),
      (Map("foo" -> null), objUnionNullString, obj("foo" -> Null)),
      (Map(), objUnionNullStringRequired, obj()),
      (Map("foo" -> null), objUnionNullStringRequired, obj("foo" -> Null)),
    )) { (data, schema, result) =>
      encoderLax.encode(data, schema) shouldBe Valid(result)
      encoderStrict.encode(data, schema) shouldBe Valid(result)
    }
  }

  ignore("should reject when missing required field") {
    val schema: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "required": ["foo"]
        |}""".stripMargin)

    new BestEffortJsonSchemaEncoder(ValidationMode.lax).encode(Map(), schema) shouldBe 'invalid
    new BestEffortJsonSchemaEncoder(ValidationMode.strict).encode(Map(), schema) shouldBe 'invalid
  }

  test("should encode avro generic record") {
    type WithError[T] = ValidatedNel[String, T]
    val avroToJsonEncoder: PartialFunction[(Any, Schema, Option[String]), WithError[Json]] = new AvroToJsonBasedOnSchemaEncoder().encoder(encoderStrict.encodeBasedOnSchema)

    val avroSchema =
      SchemaBuilder.builder().record("test").fields()
        .requiredString("field1")
        .requiredLong("field2").endRecord()

    val jsonSchema: Schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "field1": {
        |      "type": "string"
        |    },
        |    "field2": {
        |      "type": "number"
        |    }
        |  }
        |}""".stripMargin)

    val genRec = new GenericRecordBuilder(avroSchema).set("field1", "a").set("field2", 11).build()

    avroToJsonEncoder(genRec, jsonSchema, None) shouldEqual valid(obj("field1" -> fromString("a"), "field2" -> fromLong(11)))
  }
}
