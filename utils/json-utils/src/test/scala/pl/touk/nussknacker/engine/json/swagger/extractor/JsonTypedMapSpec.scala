package pl.touk.nussknacker.engine.json.swagger.extractor

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import io.circe.{Json, JsonObject}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.engine.json.swagger.extractor.JsonToNuStruct.JsonToObjectError

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetTime, ZoneOffset, ZonedDateTime}
import java.{util => ju}

class JsonTypedMapSpec extends AnyFunSuite with ValidatedValuesDetailedMessage with Matchers {

  import scala.jdk.CollectionConverters._

  private val jsonObject = JsonObject(
    "field1"       -> Json.fromString("value"),
    "field2"       -> Json.fromInt(1),
    "field4"       -> Json.fromString("2020-07-10T12:12:30+02:00"),
    "field5"       -> Json.fromString(""),
    "field6"       -> Json.fromString("12:12:35+02:00"),
    "field7"       -> Json.fromString("2020-07-10"),
    "decimalField" -> Json.fromDoubleOrNull(1.33),
    "doubleField"  -> Json.fromDoubleOrNull(1.55),
    "nullField"    -> Json.Null,
    "mapField" -> Json
      .obj(("a", Json.fromString("1")), ("b", Json.fromInt(2)), ("c", Json.fromValues(List(Json.fromString("d"))))),
    "mapOfStringsField" -> Json
      .obj(("a", Json.fromString("b")), ("c", Json.fromString("d")), ("e", Json.fromString("f"))),
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

    val value = JsonTypedMap(jsonObject, definition)

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
    val result = JsonTypedMap(jsonObject, definition)

    val mapField = result.get("mapField").asInstanceOf[TypedMap]

    val ex = intercept[JsonToObjectError](mapField.get("b"))

    ex.getMessage shouldBe "JSON returned by service has invalid type at mapField.b. Expected: SwaggerString. Returned json: 2"
    ex.path shouldBe "mapField.b"
  }

  test("should skip additionalFields when schema/SwaggerObject does not allow them") {
    val definitionWithoutFields =
      SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesDisabled)
    val result = JsonTypedMap(jsonObject, definitionWithoutFields)

    result.size() shouldBe 0
    Option(result.get("field3")) shouldBe Symbol("empty")
    result.isEmpty shouldBe true

    result shouldBe TypedMap(Map.empty)
    result shouldBe a[ju.Map[_, _]]

    val definitionWithOneField = SwaggerObject(elementType = Map("field2" -> SwaggerLong), AdditionalPropertiesDisabled)
    val result2                = JsonTypedMap(jsonObject, definitionWithOneField)
    result2 shouldEqual TypedMap(Map("field2" -> 1L))

  }

  test("should not trim additional fields fields when additionalPropertiesOn") {
    val json       = JsonObject("field1" -> Json.fromString("value"), "field2" -> Json.fromInt(1))
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong))
    val result     = JsonTypedMap(json, definition)
    result shouldBe
      TypedMap(
        Map(
          "field1" -> "value",
          "field2" -> java.math.BigDecimal.valueOf(1)
        )
      )

    val jsonIntegers = JsonObject("field1" -> Json.fromInt(2), "field2" -> Json.fromInt(1))
    val definition2 =
      SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesEnabled(SwaggerLong))
    JsonTypedMap(jsonIntegers, definition2) shouldBe
      TypedMap(
        Map(
          "field1" -> 2L,
          "field2" -> 1L
        )
      )
  }

  test("should throw exception on trying convert string to integer") {
    val json       = JsonObject("field1" -> Json.fromString("value"), "field2" -> Json.fromInt(1))
    val definition = SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesEnabled(SwaggerLong))

    val ex = intercept[JsonToObjectError] {
      JsonTypedMap(json, definition).get("field1")
    }

    ex.getMessage shouldBe """JSON returned by service has invalid type at field1. Expected: SwaggerLong. Returned json: "value""""
  }

  test("equals tests") {
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

    val map1 = JsonTypedMap(jsonObject, definition)
    val map2 = JsonTypedMap(jsonObject, definition)

    map1.equals(map2) shouldBe true
    map1 == map2 shouldBe true

    val jsonIntegers = JsonObject("field1" -> Json.fromInt(2), "field2" -> Json.fromInt(1))
    val definition2 =
      SwaggerObject(elementType = Map("field3" -> SwaggerLong), AdditionalPropertiesEnabled(SwaggerLong))
    val map3 = JsonTypedMap(jsonIntegers, definition2)
    val map4 = TypedMap(
      Map(
        "field1" -> 2L,
        "field2" -> 1L
      )
    )

    map3.equals(map4) shouldBe true
    map4.equals(map3) shouldBe true
    map3 == map4 shouldBe true
    map4 == map3 shouldBe true
  }

}
