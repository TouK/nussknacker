package pl.touk.nussknacker.engine.util.json

import io.circe.Json._
import org.json.{JSONArray, JSONObject}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonSchemaUtilsTest extends AnyFunSuite with Matchers {

  import collection.JavaConverters._

  private val someString = "str"
  private val someInt: Int = 1
  private val someLong: Long = Integer.MAX_VALUE.toLong + 1
  private val someFloat: Float = 13.5f
  private val someDouble: Double = java.lang.Float.MAX_VALUE.toDouble + 1.5

  private val BigIntBits = 63
  private val someBigInt: BigInt = BigInt.long2bigInt(someInt).setBit(BigIntBits)
  private val someBigDecimal: BigDecimal = BigDecimal.double2bigDecimal(someDouble)

  private val circeJson = obj("nested" -> obj(
    "null" -> Null,
    "string" -> fromString(someString),
    "boolean" -> fromBoolean(true),
    "int" -> fromInt(someInt),
    "long" -> fromLong(someLong),
    "float" -> fromFloatOrNull(someFloat),
    "double" -> fromDoubleOrNull(someDouble),
    "bigInt" -> fromBigInt(someBigInt),
    "bigDecimal" -> fromBigDecimal(someBigDecimal),
    "array" -> arr(
      fromInt(someInt),
      fromLong(someLong)
    )
  ))

  private val everitJson = new JSONObject(Map(
    "nested" -> new JSONObject(Map(
      "null" -> JSONObject.NULL,
      "string" -> someString,
      "boolean" -> true,
      "int" -> someInt,
      "long" -> someLong,
      "float" -> java.math.BigDecimal.valueOf(someFloat),
      "double" -> java.math.BigDecimal.valueOf(someDouble),
      "bigInt" -> java.math.BigInteger.valueOf(someInt).setBit(BigIntBits),
      "bigDecimal" -> java.math.BigDecimal.valueOf(someDouble),
      "array" -> new JSONArray(List(someInt, someLong).asJava)
    ).asJava)
  ).asJava)

  test("should convert io.circe.Json to org.everit.json") {
    val result = JsonSchemaUtils.circeToJson(circeJson)
    compare(result, everitJson)
  }

  test("should convert org.everit.json to io.circe.Json") {
    val result = JsonSchemaUtils.jsonToCirce(everitJson)
    result shouldBe circeJson
  }

  //JSONObject and JSONArray are primitives.. And comparing is done by checking reference..
  private def compare(obj: Any, expected: Any): Any = (obj, expected) match {
    case (o: JSONObject, e: JSONObject) =>
      o.keys().asScala.foreach { key =>
        compare(o.get(key), e.get(key))
      }
    case (o: JSONArray, e: JSONArray) =>
      o.toList.asScala.zipWithIndex.foreach { case (el, index) =>
        compare(el, e.get(index))
      }
    case (_, _) => obj shouldBe expected
  }
}
