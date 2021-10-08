package pl.touk.nussknacker.openapi.extractor

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import io.circe.Json
import io.circe.Json.fromString
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.openapi._
import org.scalatest.{FunSuite, Matchers}

class JsonToObjectTest extends FunSuite
  with Matchers {

  private val json = Json.obj(
    "field1" -> fromString("value"),
    "field2" -> Json.fromInt(1),
    //to jest zgodne z polską strefą - w lipcu jest +02:00...
    "field4" -> fromString("2020-07-10T12:12:30+02:00"),
    "field5" -> fromString(""),
    "decimalField" -> Json.fromDoubleOrNull(1.33),
    "doubleField" -> Json.fromDoubleOrNull(1.55)
  )

  test("should parse object with all required fields present") {
    val definition = SwaggerObject(elementType = Map(
      "field1" -> SwaggerString,
      "field2" -> SwaggerLong,
      "field3" -> SwaggerLong,
      "field4" -> SwaggerDateTime,
      "field5" -> SwaggerDateTime,
      "decimalField" -> SwaggerBigDecimal,
      "doubleField" -> SwaggerDouble
    ), required = Set("field2"))

    val value = JsonToObject(json, definition)

    value shouldBe a[TypedMap]
    val fields = value.asInstanceOf[TypedMap]
    fields.get("field1") shouldBe "value"
    fields.get("field2") shouldBe 1L
    Option(fields.get("field3")) shouldBe 'empty
    fields.get("field4") shouldBe LocalDateTime.ofInstant(ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME).toInstant, ZoneId.systemDefault())
    Option(fields.get("field5")) shouldBe 'empty
    fields.get("decimalField") shouldBe BigDecimal.valueOf(1.33).bigDecimal
    fields.get("doubleField") shouldBe 1.55
  }

  test("should fail for object with all required field absent") {
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong), required = Set("field3"))

    assertThrows[JsonToObject.JsonToObjectError] {
      JsonToObject(json, definition)
    }
  }
}
