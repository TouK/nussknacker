package pl.touk.nussknacker.engine.json.serde

import org.everit.json.schema.NumberSchema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.collection.JavaConverters._

class CirceJsonDeserializerSpec extends AnyFunSuite with ValidatedValuesDetailedMessage with Matchers {

  private val sampleLong: Long = Int.MaxValue.toLong + 1

  test("json object") {
    val schema = JsonSchemaBuilder.parseSchema(
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

    val result = new CirceJsonDeserializer(schema).deserialize(
      """{
        |  "firstName": "John",
        |  "lastName": "Doe",
        |  "age": 21
        |}""".stripMargin)

    result shouldEqual Map(
      "firstName" -> "John",
      "lastName" -> "Doe",
      "age" -> 21L
    ).asJava
  }

  test("json array") {
    val schema = JsonSchemaBuilder.parseSchema("""{"type": "array","items": {"type": "string"}}""".stripMargin)
    val result = new CirceJsonDeserializer(schema).deserialize("""["John", "Doe"]""")

    result shouldEqual List("John", "Doe").asJava
  }

  test("json object with union") {
    forAll(Table(
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
    )) { schemaString =>
      val schema = JsonSchemaBuilder.parseSchema(schemaString)
      val deserializer = new CirceJsonDeserializer(schema)

      deserializer.deserialize("""{ "a": "1"}""".stripMargin) shouldEqual Map("a" -> "1").asJava
      deserializer.deserialize("""{ "a": 1}""".stripMargin) shouldEqual Map("a" -> 1L).asJava
    }
  }

  test("handling nulls and empty json") {
    val unionSchemaWithNull = JsonSchemaBuilder.parseSchema(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": ["null", "string"]
        |    }
        |  }
        |}
        |""".stripMargin)

    val schemaWithNotRequiredField = JsonSchemaBuilder.parseSchema(
      """
        |{
        |  "type": "object",
        |  "properties": {
        |    "a": {
        |      "type": "string"
        |    }
        |  }
        |}
        |""".stripMargin)

    val schemaUnionWithDefaultField = JsonSchemaBuilder.parseSchema(
      """
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

    forAll(Table(
      ("json", "schema", "result"),
      ("""{"a": "test"}""", unionSchemaWithNull, Map("a" -> "test")),
      ("""{"a": null}""", unionSchemaWithNull, Map("a" -> null)),
      ("""{}""", unionSchemaWithNull, Map()),
      ("""{}""", schemaWithNotRequiredField, Map()),
      ("""{}""", schemaUnionWithDefaultField, Map("a" -> "lcl")),
      ("""{"a": null}""", schemaUnionWithDefaultField, Map("a" -> null)),
    )) { (json, schema, result) =>
      val deserializer = new CirceJsonDeserializer(schema)
      deserializer.deserialize(json) shouldEqual result.asJava
    }
  }

  test("handle number / integer schema") {
    val integerSchema = NumberSchema.builder().requiresInteger(true).build()
    val numberSchema = NumberSchema.builder().build()

    forAll(Table(
      ("json", "schema", "expected"),
      (1.toString, integerSchema, 1),
      (sampleLong.toString, integerSchema, sampleLong),
      //It's a little bit tricky, big int is rounded to Long.Max by JsonToNuStruct because for integer schema we assign long type from OpenApi schema
      (BigInt.long2bigInt(sampleLong).setBit(67).toString(), integerSchema, Long.MaxValue),
      (sampleLong.toString, numberSchema, java.math.BigDecimal.valueOf(sampleLong)),
      (sampleLong.toDouble.toString, numberSchema, java.math.BigDecimal.valueOf(sampleLong)),
    )) { (json, schema, expected) =>
      val deserializer = new CirceJsonDeserializer(schema)
      val result = deserializer.deserialize(json)
      result shouldEqual expected
    }

    assertThrows[CustomNodeValidationException] {
      new CirceJsonDeserializer(integerSchema).deserialize("1.0")
    }
  }

}
