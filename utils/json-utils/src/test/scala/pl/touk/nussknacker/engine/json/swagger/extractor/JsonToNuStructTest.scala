package pl.touk.nussknacker.engine.json.swagger.extractor

import io.circe.Json
import io.circe.Json.{fromBoolean, fromInt, fromLong, fromString, fromValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZoneOffset, ZonedDateTime}

class JsonToNuStructTest extends AnyFunSuite with Matchers {

  private val json = Json.obj(
    "field1" -> fromString("value"),
    "field2" -> Json.fromInt(1),
    "field4" -> fromString("2020-07-10T12:12:30+02:00"),
    "field5" -> fromString(""),
    "field6" -> fromString("12:12:35+02:00"),
    "field7" -> fromString("2020-07-10"),
    "decimalField" -> Json.fromDoubleOrNull(1.33),
    "doubleField" -> Json.fromDoubleOrNull(1.55),
    "nullField" -> Json.Null,
    "mapField" -> Json.obj(("a", fromString("1")), ("b", fromInt(2)), ("c", fromValues(List(fromString("d"))))),
    "mapOfStringsField" -> Json.obj(("a", fromString("b")), ("c", fromString("d")), ("e", fromString("f"))),
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
        "doubleField" -> SwaggerDouble,
        "nullField" -> SwaggerNull,
        "mapField" -> SwaggerMap(None),
        "mapOfStringsField" -> SwaggerMap(Some(SwaggerString))
      )
    )

    val value = JsonToNuStruct(json, definition)

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
    fields.get("nullField").asInstanceOf[AnyRef] shouldBe null
    val mapField = fields.get("mapField").asInstanceOf[TypedMap]
    mapField.get("a") shouldBe "1"
    mapField.get("b") shouldBe 2
    mapField.get("c") shouldBe a[java.util.List[_]]
    fields.get("mapOfStringsField") shouldBe a[TypedMap]
  }

  test("should reject map with incorrect values types") {
    val definition = SwaggerObject(elementType = Map("mapField" -> SwaggerMap(Some(SwaggerString))))

    val ex = intercept[JsonToObjectError](JsonToNuStruct(json, definition))

    ex.getMessage shouldBe "JSON returned by service has invalid type at mapField.b. Expected: SwaggerString. Returned json: 2"
    ex.path shouldBe "mapField.b"
  }

  test("should trim useless fields") {
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong))
    extractor.JsonToNuStruct(json, definition) shouldBe TypedMap(Map.empty)
  }

  test("should parse union") {
    val definition =
      SwaggerObject(elementType = Map("field2" -> SwaggerUnion(List(SwaggerString, SwaggerLong))))

    val value = JsonToNuStruct(json, definition)

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
        "nested" -> SwaggerObject(elementType = Map("string" -> SwaggerString))
      )
    )

    def assertPath(json: Json, path: String) =
      intercept[JsonToObjectError](JsonToNuStruct(json, definition)).path shouldBe path

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
