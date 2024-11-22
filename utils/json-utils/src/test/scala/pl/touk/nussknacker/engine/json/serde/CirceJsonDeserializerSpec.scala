package pl.touk.nussknacker.engine.json.serde

import org.everit.json.schema.NumberSchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.time.LocalDate
import scala.jdk.CollectionConverters._

class CirceJsonDeserializerSpec extends AnyFunSuite with ValidatedValuesDetailedMessage with Matchers {

  private val sampleLong: Long = Int.MaxValue.toLong + 1

  test("json object") {
    val schema = JsonSchemaBuilder.parseSchema("""{
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

    val result = new CirceJsonDeserializer(schema).deserialize("""{
        |  "firstName": "John",
        |  "lastName": "Doe",
        |  "age": 21
        |}""".stripMargin)

    result shouldEqual Map(
      "firstName" -> "John",
      "lastName"  -> "Doe",
      "age"       -> 21L
    ).asJava
  }

  test("json array") {
    val schema = JsonSchemaBuilder.parseSchema("""{"type": "array","items": {"type": "string"}}""".stripMargin)
    val result = new CirceJsonDeserializer(schema).deserialize("""["John", "Doe"]""")

    result shouldEqual List("John", "Doe").asJava
  }

  test("json object with union") {
    forAll(
      Table(
        "schema",
        """{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": ["string", "integer"]
        |    }
        |  }
        |}""".stripMargin,
        """{
        |  "type": "object",
        |  "properties": {
        |    "a": {
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
        |    "a": {
        |      "oneOf": [
        |        { "type": "string" },
        |        { "type": "integer" }
        |      ]
        |    }
        |  }
        |}""".stripMargin
      )
    ) { schemaString =>
      val schema       = JsonSchemaBuilder.parseSchema(schemaString)
      val deserializer = new CirceJsonDeserializer(schema)

      deserializer.deserialize("""{ "a": "1"}""".stripMargin) shouldEqual Map("a" -> "1").asJava
      deserializer.deserialize("""{ "a": 1}""".stripMargin) shouldEqual Map("a" -> 1L).asJava
    }
  }

  test("handling nulls and empty json") {
    val unionSchemaWithNull = JsonSchemaBuilder.parseSchema("""
        |{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": ["null", "string"]
        |    }
        |  }
        |}
        |""".stripMargin)

    val schemaWithNotRequiredField = JsonSchemaBuilder.parseSchema("""
        |{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": "string"
        |    }
        |  }
        |}
        |""".stripMargin)

    val schemaUnionWithDefaultField = JsonSchemaBuilder.parseSchema("""
        |{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": ["null", "string"],
        |      "default": "lcl"
        |    }
        |  }
        |}
        |""".stripMargin)

    forAll(
      Table(
        ("json", "schema", "result"),
        ("""{"a": "test"}""", unionSchemaWithNull, Map("a" -> "test")),
        ("""{"a": null}""", unionSchemaWithNull, Map("a" -> null)),
        ("""{}""", unionSchemaWithNull, Map()),
        ("""{}""", schemaWithNotRequiredField, Map()),
        ("""{}""", schemaUnionWithDefaultField, Map("a" -> "lcl")),
        ("""{"a": null}""", schemaUnionWithDefaultField, Map("a" -> null)),
      )
    ) { (json, schema, result) =>
      val deserializer = new CirceJsonDeserializer(schema)
      deserializer.deserialize(json) shouldEqual result.asJava
    }
  }

  test("handle number / integer schema") {
    val integerSchema = NumberSchema.builder().requiresInteger(true).build()
    val numberSchema  = NumberSchema.builder().build()

    forAll(
      Table(
        ("json", "schema", "expected"),
        (1.toString, integerSchema, 1),
        (sampleLong.toString, integerSchema, sampleLong),
        // It's a little bit tricky, big int is rounded to Long.Max by JsonToNuStruct because for integer schema we assign long type from OpenApi schema
        (BigInt.long2bigInt(sampleLong).setBit(67).toString(), integerSchema, Long.MaxValue),
        (sampleLong.toString, numberSchema, java.math.BigDecimal.valueOf(sampleLong)),
        (sampleLong.toDouble.toString, numberSchema, java.math.BigDecimal.valueOf(sampleLong)),
      )
    ) { (json, schema, expected) =>
      val deserializer = new CirceJsonDeserializer(schema)
      val result       = deserializer.deserialize(json)
      result shouldEqual expected
    }

    assertThrows[CustomNodeValidationException] {
      new CirceJsonDeserializer(integerSchema).deserialize("1.0")
    }
  }

  test("json object with defined, pattern and additional properties") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "someDefinedProp": {
        |      "type": "boolean"
        |    }
        |  },
        |  "additionalProperties": {
        |    "type": "string"
        |  },
        |  "patternProperties": {
        |    "_int$": {
        |      "type": "integer"
        |    }
        |  }
        |}""".stripMargin)

    val result = new CirceJsonDeserializer(schema).deserialize("""{
        |  "someDefinedProp": true,
        |  "someAdditionalProp": "string",
        |  "somePatternProp_int": 1234
        |}""".stripMargin)

    result shouldEqual Map(
      "someDefinedProp"     -> true,
      "someAdditionalProp"  -> "string",
      "somePatternProp_int" -> 1234L
    ).asJava
  }

  test("json object pattern properties and with disabled additional properties") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": false,
        |  "patternProperties": {
        |    "_int$": {
        |      "type": "integer"
        |    }
        |  }
        |}""".stripMargin)

    val result = new CirceJsonDeserializer(schema).deserialize("""{
        |  "somePatternProp_int": 1234
        |}""".stripMargin)

    result shouldEqual Map(
      "somePatternProp_int" -> 1234L
    ).asJava
  }

  test("json object pattern properties when no explicit properties") {
    val schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "additionalProperties": {
        |    "type": "string"
        |  },
        |  "patternProperties": {
        |    "_int$": {
        |      "type": "integer"
        |    },
        |    "_date$": {
        |      "type": "string",
        |      "format": "date"
        |    }
        |  }
        |}""".stripMargin)

    val result = new CirceJsonDeserializer(schema).deserialize("""{
        |  "somePatternProp_int": 1234,
        |  "somePatternProp_date": "2023-01-23",
        |  "someAdditionalProp": "1234"
        |}""".stripMargin)

    result shouldEqual Map(
      "somePatternProp_int"  -> 1234L,
      "somePatternProp_date" -> LocalDate.parse("2023-01-23"),
      "someAdditionalProp"   -> "1234"
    ).asJava
  }

  test("handle 'everything allowed' schema in additionalProperties properly") {
    val emptySchemaInAdditionalProperties =
      """{
        |  "type": "object",
        |  "additionalProperties": {}
        |}""".stripMargin
    val implicitAdditionalProperties =
      """{
        |  "type": "object"
        |}""".stripMargin
    val trueInAdditionalProperties =
      """{
        |  "type": "object",
        |  "additionalProperties": true
        |}""".stripMargin

    forAll(
      Table(
        ("schema"),
        (emptySchemaInAdditionalProperties),
        (implicitAdditionalProperties),
        (trueInAdditionalProperties),
      )
    ) { schemaStr =>
      val schema = JsonSchemaBuilder.parseSchema(schemaStr.stripMargin)
      val result = new CirceJsonDeserializer(schema).deserialize("""{
          |  "additionalInt": 1234,
          |  "additionalString": "foo",
          |  "additionalObject": {"foo": "bar"}
          |}""".stripMargin)

      result shouldEqual Map(
        "additionalInt"    -> 1234,
        "additionalString" -> "foo",
        "additionalObject" -> Map("foo" -> "bar").asJava
      ).asJava
    }
  }

  test("handle empty always true schema") {
    forAll(
      Table(
        ("json", "schema", "expected"),
        ("{}", "{}", Map.empty.asJava),
        ("{}", "true", Map.empty.asJava),
        ("\"foo\"", "{}", "foo"),
        ("\"foo\"", "true", "foo"),
        ("{\"foo\": \"bar\"}", "{}", Map("foo" -> "bar").asJava),
        ("{\"foo\": \"bar\"}", "true", Map("foo" -> "bar").asJava),
      )
    ) { (json, schemaStr, expected) =>
      val schema       = JsonSchemaBuilder.parseSchema(schemaStr.stripMargin)
      val deserializer = new CirceJsonDeserializer(schema)
      val result       = deserializer.deserialize(json)
      result shouldEqual expected
    }
  }

  test("handle refs") {
    val schema = JsonSchemaBuilder.parseSchema(
      """{
        |  "type": "object",
        |  "$defs": {
        |    "SomeRef": {
        |      "anyOf": [
        |        {
        |          "type": "integer"
        |        },
        |        {
        |          "type": "null"
        |        }
        |      ],
        |      "default": null
        |    }
        |  },
        |  "properties": {
        |    "field": {
        |      "$ref": "#/$defs/SomeRef"
        |    }
        |  }
        |}""".stripMargin
    )
    val deserializer = new CirceJsonDeserializer(schema)

    forAll(
      Table(
        ("json", "expected"),
        (
          "{}",
          Map.empty.asJava
        ),
        (
          """{"field": null}""",
          Map("field" -> null).asJava
        ),
        (
          """{"field": 123}""",
          Map("field" -> 123L).asJava
        )
      )
    ) { (json, expected) =>
      val result = deserializer.deserialize(json)
      result shouldEqual expected
    }

  }

}
