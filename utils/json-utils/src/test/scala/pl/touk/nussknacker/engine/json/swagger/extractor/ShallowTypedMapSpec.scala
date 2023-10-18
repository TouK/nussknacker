package pl.touk.nussknacker.engine.json.swagger.extractor

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import io.circe._
import io.circe.Json.{fromBoolean, fromInt, fromLong, fromString, fromValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZoneOffset, ZonedDateTime}
import java.{util => ju}

class ShallowTypedMapSpec extends AnyFunSuite with ValidatedValuesDetailedMessage with Matchers {

  import scala.jdk.CollectionConverters._

  private val jsonObject = JsonObject(
    "field1"       -> fromString("value"),
    "field2"       -> Json.fromInt(1),
    "field4"       -> fromString("2020-07-10T12:12:30+02:00"),
    "field5"       -> fromString(""),
    "field6"       -> fromString("12:12:35+02:00"),
    "field7"       -> fromString("2020-07-10"),
    "decimalField" -> Json.fromDoubleOrNull(1.33),
    "doubleField"  -> Json.fromDoubleOrNull(1.55),
    "nullField"    -> Json.Null,
    "mapField"     -> Json.obj(("a", fromString("1")), ("b", fromInt(2)), ("c", fromValues(List(fromString("d"))))),
    "mapOfStringsField" -> Json.obj(("a", fromString("b")), ("c", fromString("d")), ("e", fromString("f"))),
  )

  test("should parse object with all required fields present") {
    val definition = SwaggerObject(
      elementType = Map(
        "field1"            -> SwaggerString,
        "field2"            -> SwaggerLong,
        "field3"            -> SwaggerLong,
        "field4"            -> SwaggerDateTime,
        "field5"            -> SwaggerDateTime,
        "field6"            -> SwaggerTime,
        "field7"            -> SwaggerDate,
        "decimalField"      -> SwaggerBigDecimal,
        "doubleField"       -> SwaggerDouble,
        "nullField"         -> SwaggerNull,
        "mapField"          -> SwaggerObject(Map.empty),
        "mapOfStringsField" -> SwaggerObject(Map.empty, AdditionalPropertiesEnabled(SwaggerString))
      ),
      AdditionalPropertiesDisabled
    )

    val value = ShallowTypedMap(jsonObject, definition)

    value shouldBe a[TypedMap]

    value.get("nullField").asInstanceOf[AnyRef] shouldBe null

    value.keySet().asScala shouldBe Set(
      "field5",
      "doubleField",
      "field1",
      "field4",
      "mapField",
      "field2",
      "field7",
      "field6",
      "decimalField",
      "nullField",
      "mapOfStringsField"
    )

    Set(
      "field5",
      "doubleField",
      "field1",
      "field4",
      "mapField",
      "field2",
      "field7",
      "field6",
      "decimalField",
      "nullField",
      "mapOfStringsField"
    ) -- value.entrySet().asScala.map(_.getKey) shouldBe Set()

    value.entrySet().asScala.map(_.getKey) shouldBe Set(
      "field5",
      "doubleField",
      "field1",
      "field4",
      "mapField",
      "field2",
      "field7",
      "field6",
      "decimalField",
      "nullField",
      "mapOfStringsField"
    )

    value.containsKey("field1") shouldBe true
    value.size() shouldBe 11
    value.isEmpty shouldBe false
    value.get("field1") shouldBe "value"
    value.get("field2") shouldBe 1L
    Option(value.get("field3")) shouldBe Symbol("empty")
    value.get("field4") shouldBe ZonedDateTime.parse("2020-07-10T12:12:30+02:00", DateTimeFormatter.ISO_DATE_TIME)
    Option(value.get("field5")) shouldBe Symbol("empty")
    value.get("field6") shouldBe OffsetTime.of(12, 12, 35, 0, ZoneOffset.ofHours(2))
    value.get("field7") shouldBe LocalDate.parse("2020-07-10", DateTimeFormatter.ISO_LOCAL_DATE)
    value.get("decimalField") shouldBe BigDecimal.valueOf(1.33).bigDecimal
    value.get("doubleField") shouldBe 1.55
    value.get("nullField").asInstanceOf[AnyRef] shouldBe null
    val mapField = value.get("mapField").asInstanceOf[TypedMap]
    mapField.get("a") shouldBe "1"
    mapField.get("b") shouldBe java.math.BigDecimal.valueOf(2)
    mapField.get("c") shouldBe a[java.util.List[_]]
    value.get("mapOfStringsField") shouldBe a[TypedMap]
  }

  test("should reject map with incorrect values types") {
    val definition = SwaggerObject(
      elementType = Map("mapField" -> SwaggerObject(Map.empty, AdditionalPropertiesEnabled(SwaggerString))),
      AdditionalPropertiesDisabled
    )
    val result = ShallowTypedMap(jsonObject, definition)

    val mapField = result.get("mapField").asInstanceOf[TypedMap]

    val ex = intercept[JsonToObjectError](mapField.get("b"))

    ex.getMessage shouldBe "JSON returned by service has invalid type at mapField.b. Expected: SwaggerString. Returned json: 2"
    ex.path shouldBe "mapField.b"
  }

  test("should skip additionalFields when schema/SwaggerObject does not allow them") {
    val definitionWithoutFields =
      SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesDisabled)
    val result = ShallowTypedMap(jsonObject, definitionWithoutFields)

    result.size() shouldBe 0
    Option(result.get("field3")) shouldBe Symbol("empty")
    result.isEmpty shouldBe true

    result shouldBe TypedMap(Map.empty)
    result shouldBe a[ju.Map[_, _]]

    val definitionWithOneField = SwaggerObject(elementType = Map("field2" -> SwaggerLong), AdditionalPropertiesDisabled)
    val result2                = ShallowTypedMap(jsonObject, definitionWithOneField)
    result2 shouldEqual TypedMap(Map("field2" -> 1L)).asInstanceOf[ju.Map[String, Any]]

  }

  test("should not trim additional fields fields when additionalPropertiesOn") {
    val json       = JsonObject("field1" -> fromString("value"), "field2" -> Json.fromInt(1))
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong))
    val result     = ShallowTypedMap(json, definition)
    result.asScala shouldBe
      Map(
        "field1" -> "value",
        "field2" -> java.math.BigDecimal.valueOf(1)
      )

    val jsonIntegers = JsonObject("field1" -> fromInt(2), "field2" -> fromInt(1))
    val definition2 =
      SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesEnabled(SwaggerLong))
    ShallowTypedMap(jsonIntegers, definition2).asScala shouldBe
      Map(
        "field1" -> 2L,
        "field2" -> 1L
      )
  }

  test("should throw exception on trying convert string to integer") {
    val json       = JsonObject("field1" -> fromString("value"), "field2" -> Json.fromInt(1))
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesEnabled(SwaggerLong))

    val ex = intercept[JsonToObjectError] {
      ShallowTypedMap(json, definition).extractValue("field1")
    }

    ex.getMessage shouldBe """JSON returned by service has invalid type at field1. Expected: SwaggerLong. Returned json: "value""""
  }

}
