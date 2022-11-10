package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.Json
import io.circe.Json.{fromBoolean, fromLong, fromString, fromValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToTypedMap.JsonToObjectError

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZoneOffset, ZonedDateTime}

class JsonToTypedMapTest extends AnyFunSuite with Matchers {

  private val json = Json.obj(
    "field1" -> fromString("value"),
    "field2" -> Json.fromInt(1),
    "field4" -> fromString("2020-07-10T12:12:30+02:00"),
    "field5" -> fromString(""),
    "field6" -> fromString("12:12:35+02:00"),
    "field7" -> fromString("2020-07-10"),
    "decimalField" -> Json.fromDoubleOrNull(1.33),
    "doubleField" -> Json.fromDoubleOrNull(1.55)
  )

  test("should parse object with all required fields present") {
    val definition = SwaggerObject(
      elementType = Map(
        "field1" -> SwaggerString,
        "field2" -> SwaggerLong,
        "field3" -> SwaggerLong,
        "field4" -> SwaggerDateTime,
        "field5" -> SwaggerDateTime,
        "field6" -> SwaggerTime,
        "field7" -> SwaggerDate,
        "decimalField" -> SwaggerBigDecimal,
        "doubleField" -> SwaggerDouble
      ),
      required = Set("field2")
    )

    val value = JsonToTypedMap(json, definition)

    value shouldBe a[TypedMap]
    val fields = value.asInstanceOf[TypedMap]
    fields.get("field1") shouldBe "value"
    fields.get("field2") shouldBe 1L
    Option(fields.get("field3")) shouldBe 'empty
    fields.get("field4") shouldBe ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    Option(fields.get("field5")) shouldBe 'empty
    fields.get("field6") shouldBe OffsetTime.of(12, 12, 35, 0, ZoneOffset.ofHours(2))
    fields.get("field7") shouldBe LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    fields.get("decimalField") shouldBe BigDecimal.valueOf(1.33).bigDecimal
    fields.get("doubleField") shouldBe 1.55
  }

  test("should fail for object with all required field absent") {
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong), required = Set("field3"))

    assertThrows[JsonToTypedMap.JsonToObjectError] {
      extractor.JsonToTypedMap(json, definition)
    }
  }

  test("should parse union") {
    val definition =
      SwaggerObject(elementType = Map("field2" -> SwaggerUnion(List(SwaggerString, SwaggerLong))), required = Set())

    val value = JsonToTypedMap(json, definition)

    value shouldBe a[TypedMap]
    val fields = value.asInstanceOf[TypedMap]
    fields.get("field2") shouldBe 1L
  }

  test("should handle display path in error") {
    val definition = SwaggerObject(
      elementType = Map(
        "string" -> SwaggerString,
        "long" -> SwaggerLong,
        "array" -> SwaggerArray(SwaggerBool),
        "nested" -> SwaggerObject(elementType = Map("string" -> SwaggerString), required = Set("string"))
      ),
      required = Set("string", "long", "array", "nested")
    )

    def assertPath(json: Json, path: String) =
      intercept[JsonToObjectError](JsonToTypedMap(json, definition)).path shouldBe path

    assertPath(Json.obj("string" -> fromLong(1)), "string")
    assertPath(
      Json.obj(
        "string" -> fromString(""),
        "long" -> fromLong(1),
        "array" -> fromValues(List(fromBoolean(false), fromString("string")))
      ),
      "array[1]"
    )
    assertPath(
      Json.obj(
        "string" -> fromString(""),
        "long" -> fromLong(1),
        "array" -> fromValues(Nil),
        "nested" -> Json.obj("string" -> fromLong(1))
      ),
      "nested.string"
    )
  }
}
