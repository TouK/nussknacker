package pl.touk.nussknacker.engine.json.serde

import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.collection.JavaConverters._

class CirceJsonDeserializerSpec extends FunSuite with ValidatedValuesDetailedMessage with Matchers {

  test("json object") {
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
    val result = new CirceJsonDeserializer(schema).deserialize(
      """{
        |  "firstName": "John",
        |  "lastName": "Doe",
        |  "age": 21
        |}""".stripMargin)

    result.validValue shouldEqual Map(
      "firstName" -> "John",
      "lastName" -> "Doe",
      "age" -> 21L
    ).asJava
  }

  test("json array") {
    val schema = SchemaLoader.load(new JSONObject(
      """{
        |  "$schema": "https://json-schema.org/draft-07/schema",
        |  "type": "array",
        |  "items": {
        |    "type": "string"
        |  }
        |}""".stripMargin))
    val result = new CirceJsonDeserializer(schema).deserialize("""["John", "Doe"]""")

    result.validValue shouldEqual List("John", "Doe").asJava
  }

}
