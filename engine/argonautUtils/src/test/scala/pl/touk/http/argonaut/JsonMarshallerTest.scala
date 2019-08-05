package pl.touk.http.argonaut

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}

class JsonMarshallerTest extends FunSuite
  with Matchers
  with TableDrivenPropertyChecks {

  import argonaut._
  import Argonaut._

  private val marshallers = Table("marshaller", ArgonautJsonMarshaller, JacksonJsonMarshaller)
  private val twoFieldsJson = Json("first" -> jString("1"), "second" -> jNumber(2))
  private val json = Json(
    "string" -> jString("1"),
    "number" -> jNumber(2),
    "array" -> jArray(List(jString("1"), twoFieldsJson)),
    "oneElementArray" -> jArray(List(jNumber(5))),
    "emptyArray" -> jArray(Nil),
    "object" -> twoFieldsJson,
    "emptyObject" -> jEmptyObject
  )

  test("should print json with no spaces") {
    val noSpacesJson = json.nospaces

    forAll(marshallers) { marshaller =>
      marshaller.marshallToString(json) shouldBe noSpacesJson
    }
  }

  test("should pretty print json with argonaut") {
    val prettyJson = json.spaces2

    ArgonautJsonMarshaller.marshallToString(json, MarshallOptions(pretty = true)) shouldBe prettyJson
  }

  test("should pretty print json with jackson") {
    val prettyJson = json.spaces2

    JacksonJsonMarshaller.marshallToString(json, MarshallOptions(pretty = true)) shouldBe prettyJson
        .replace("\"emptyArray\" : []", "\"emptyArray\" : [ ]")
        .replace("\"emptyObject\" : {\n    \n  }", "\"emptyObject\" : { }")
  }
}
