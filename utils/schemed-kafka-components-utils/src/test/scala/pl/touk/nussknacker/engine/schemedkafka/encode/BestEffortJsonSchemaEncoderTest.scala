package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.Validated.{Invalid, Valid, valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.circe.Json
import io.circe.Json.{Null, fromBoolean, fromInt, fromLong, fromString, obj}
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.{TableFor2, TableFor3}
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.json.encode.BestEffortJsonSchemaEncoder
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZonedDateTime}

class BestEffortJsonSchemaEncoderTest extends AnyFunSuite {

  private val encoder = new BestEffortJsonSchemaEncoder

  private val objString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": "string"
      |    }
      |  },
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objRequiredString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": "string"
      |    }
      |  },
      |  "required": ["foo"],
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objWithAdditionalProperties: Schema = JsonSchemaBuilder.parseSchema(
    """{"type": "object", "additionalProperties": true}""".stripMargin
  )

  private val objWithAdditionalExactProperties: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "additionalProperties": {
      |     "type":"integer"
      |  }
      |}""".stripMargin)

  private val objRequiredWithDefault: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": "string",
      |      "default": "default"
      |    }
      |  },
      |  "required": ["foo"],
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": ["null", "string"]
      |    }
      |  },
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objRequiredUnionNullString: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": ["null", "string"]
      |    }
      |  },
      |  "required": ["foo"],
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objUnionNullStringWithDefault: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": ["null", "string"],
      |      "default": "default"
      |    }
      |  },
      |  "additionalProperties": false
      |}""".stripMargin)

  private val objRequiredUnionNullStringWithDefault: Schema = JsonSchemaBuilder.parseSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "foo": {
      |      "type": ["null", "string"],
      |      "default": "default"
      |    }
      |  },
      |  "required": ["foo"],
      |  "additionalProperties": false
      |}""".stripMargin)

  private val anyInput: Map[String, Any] = Map("f1" -> 1, "f2" -> true, "map" -> Map("str" -> "test"))

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

  test("should encode date time") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "date-time"
        |}""".stripMargin))

    val zdt = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    val encodedZdt = encoder.encode(zdt, schema)
    encodedZdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))

    val encodedOdt = encoder.encode(zdt.toOffsetDateTime, schema)
    encodedOdt shouldEqual Valid(Json.fromString("2020-07-10T12:12:30+02:00"))
  }

  test("should encode date") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "date"
        |}""".stripMargin))

    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)

    val encoded = encoder.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("2020-07-10"))
  }

  test("should encode time") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "time"
        |}""".stripMargin))

    val date = OffsetTime.parse("20:20:39+01:00", DateTimeFormatter.ISO_OFFSET_TIME)

    val encoded = encoder.encode(date, schema)

    encoded shouldEqual Valid(Json.fromString("20:20:39+01:00"))
  }

  test("should throw when wrong date-time") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "date-time"
        |}""".stripMargin))

    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)

    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong time") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "time"
        |}""".stripMargin))

    val date = LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)

    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
  }

  test("should throw when wrong date") {
    val schema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "string",
        |  "format": "date"
        |}""".stripMargin))

    val date = ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)

    val encoded = encoder.encode(date, schema)

    encoded shouldBe 'invalid
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

    encoder.encode("1", schema) shouldEqual Invalid(NonEmptyList("Not expected type: java.lang.String for field: None with schema: {\"type\":\"number\",\"$schema\":\"https://json-schema.org/draft-07/schema\"}", List()))
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
      val schema: Schema = SchemaLoader.load(new JSONObject(schemaString))
      encoder.encode(Map("foo" -> 1), schema) shouldBe Valid(Json.obj(("foo", Json.fromLong(1L))))
      encoder.encode(Map("foo" -> "1"), schema) shouldBe Valid(Json.obj(("foo", Json.fromString("1"))))
    }
  }

  test("handling encode with additionalProperties") {
    val expected1 = obj(
      "f1" -> fromInt(1),
      "f2" -> fromBoolean(true),
      "map" -> obj("str" -> fromString("test"))
    )

    val expected2 = obj("f1" -> fromInt(1), "f2" -> fromInt(2))

    val table: TableFor3[Map[String, Any], Schema, Json] = Table(
      ("data", "schema", "expected"),
      (anyInput, objWithAdditionalProperties, expected1),
      (Map("f1" -> 1, "f2" -> 2), objWithAdditionalExactProperties, expected2)
    )

    runWithSuccess(table)
  }

  test("handling encode without additionalProperties") {
    val table: TableFor2[Map[String, Any], Schema] = Table(
      ("data", "schema"),
      (anyInput, objString),
      (anyInput, objWithAdditionalExactProperties)
    )

    runWithFailed(table)
  }

  test("use cases of handling encode null and empty map value") {
    val table: TableFor3[Map[String, Any], Schema, Json] = Table(
      ("data", "schema", "expected"),
      (Map(), objString, obj()),
      (Map(), objUnionNullString, obj()),
      (Map(), objUnionNullStringWithDefault, obj()),
      (Map("foo" -> null), objUnionNullString, obj("foo" -> Null)),
      (Map("foo" -> null), objRequiredUnionNullString, obj("foo" -> Null)),
    )

    runWithSuccess(table)
  }

  test("use cases of failing handling encode null and empty map value") {
    val table: TableFor2[Map[String, Any], Schema] = Table(
      ("data", "schema"),
      (Map("foo" -> null), objString),
      (Map(), objRequiredString),
      (Map(), objRequiredUnionNullString),
      (Map(), objRequiredWithDefault), //default doesn't work when required is set
      (Map(), objRequiredUnionNullStringWithDefault), //default doesn't work when required is set
    )

    runWithFailed(table)
  }

  private def runWithSuccess(table: TableFor3[Map[String, Any], Schema, Json]): Assertion =
    forAll(table) { (data, schema, expected) =>
      encoder.encode(data, schema) shouldBe Valid(expected)
    }

  private def runWithFailed(table: TableFor2[Map[String, Any], Schema]): Assertion =
    forAll(table) { (data, schema) =>
      encoder.encode(data, schema) shouldBe 'invalid
    }

  test("should encode avro generic record") {
    type WithError[T] = ValidatedNel[String, T]
    val avroToJsonEncoder: PartialFunction[(Any, Schema, Option[String]), WithError[Json]] = new AvroToJsonBasedOnSchemaEncoder().encoder(encoder.encodeBasedOnSchema)

    val avroSchema =
      SchemaBuilder.builder().record("test").fields()
        .requiredString("field1")
        .requiredLong("field2").endRecord()

    val jsonSchema: Schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "object",
        |  "properties": {
        |    "field1": {
        |      "type": "string"
        |    },
        |    "field2": {
        |      "type": "number"
        |    }
        |  }
        |}""".stripMargin))

    val genRec = new GenericRecordBuilder(avroSchema).set("field1", "a").set("field2", 11).build()

    avroToJsonEncoder(genRec, jsonSchema, None) shouldEqual valid(obj("field1" -> fromString("a"), "field2" -> fromLong(11)))
  }
}
