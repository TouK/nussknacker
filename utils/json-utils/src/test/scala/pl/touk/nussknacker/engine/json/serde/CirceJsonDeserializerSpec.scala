package pl.touk.nussknacker.engine.json.serde

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.json.JsonSchemaParser

import scala.collection.JavaConverters._

class CirceJsonDeserializerSpec extends AnyFunSuite with ValidatedValuesDetailedMessage with Matchers {

  test("json object") {
    val schema = JsonSchemaParser.parseSchema(
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
    val schema = JsonSchemaParser.parseSchema(
      """{
        |  "type": "array",
        |  "items": {
        |    "type": "string"
        |  }
        |}""".stripMargin)

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
      val schema = JsonSchemaParser.parseSchema(schemaString)
      val deserializer = new CirceJsonDeserializer(schema)

      deserializer.deserialize(
        """{
          |  "a": "1",
          |}""".stripMargin) shouldEqual Map("a" -> "1").asJava

      deserializer.deserialize(
        """{
          |  "a": 1,
          |}""".stripMargin) shouldEqual Map("a" -> 1L).asJava
    }
  }

  test("json object with default field") {
    val schema = JsonSchemaParser.parseSchema(
      """{
        |  "type": "object",
        |  "properties": {
        |    "a": { "type": "string", "default": "lcl" }
        |  }
        |}""".stripMargin)

    val result = new CirceJsonDeserializer(schema).deserialize("""{}""")

    result shouldEqual Map("a" -> "lcl").asJava
  }

}
