package pl.touk.nussknacker.engine.util.json

import io.circe.Json
import io.circe.Json._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.test.ClassLoaderWithServices

import java.time._
import java.util
import java.util.UUID
import scala.collection.immutable.{ListMap, ListSet}

class ToJsonEncoderSpec extends AnyFunSpec with Matchers {

  private val encoder = ToJsonEncoder.default

  it("should encode simple elements as a json") {
    encoder.encodeUnsafe(1) shouldEqual fromLong(1)
    encoder.encodeUnsafe(BigDecimal.valueOf(2.34)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.34))
    encoder.encodeUnsafe(new java.math.BigDecimal("12.34")) shouldEqual fromBigDecimal(
      new java.math.BigDecimal("12.34")
    )
    encoder.encodeUnsafe(BigDecimal("12.34")) shouldEqual fromBigDecimal(BigDecimal("12.34"))
    encoder.encodeUnsafe(new java.math.BigInteger("1234")) shouldEqual fromBigInt(new java.math.BigInteger("1234"))
    encoder.encodeUnsafe(BigInt("1234")) shouldEqual fromBigInt(BigInt("1234"))
    encoder.encodeUnsafe(12.34f) shouldEqual fromFloatOrNull(12.34f)
    encoder.encodeUnsafe(java.math.BigDecimal.valueOf(2.0)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encodeUnsafe(2.0) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encodeUnsafe("ala") shouldEqual fromString("ala")
    encoder.encodeUnsafe(true) shouldEqual fromBoolean(true)
    encoder.encodeUnsafe(java.lang.Boolean.TRUE) shouldEqual fromBoolean(true)
    encoder.encodeUnsafe(LocalDateTime.of(2020, 9, 12, 11, 55, 33, 0)) shouldEqual fromString("2020-09-12T11:55:33")

    encoder.encodeUnsafe(LocalDate.of(2020, 9, 12)) shouldEqual fromString("2020-09-12")
    encoder.encodeUnsafe(LocalTime.of(11, 55, 33)) shouldEqual fromString("11:55:33")

    val zonedTime = ZonedDateTime.of(2020, 9, 12, 11, 55, 33, 0, ZoneId.of("Europe/Warsaw"))
    encoder.encodeUnsafe(zonedTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    encoder.encodeUnsafe(zonedTime.toOffsetDateTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    // Default Instant encoding is in Z
    encoder.encodeUnsafe(zonedTime.toInstant) shouldEqual fromString("2020-09-12T09:55:33Z")

    val uuid = UUID.randomUUID()
    encoder.encodeUnsafe(uuid) shouldEqual fromString(uuid.toString)
    encoder.encodeUnsafe(SampleEnum.LOREM) shouldEqual fromString("LOREM")
  }

  it("should handle optional elements as a json") {
    encoder.encodeUnsafe(Some("ala")) shouldEqual fromString("ala")
    encoder.encodeUnsafe(None) shouldEqual Null
    encoder.encodeUnsafe(null) shouldEqual Null
  }

  it("should encode collections as a json") {
    encoder.encodeUnsafe(List(1, 2, "3")) shouldEqual fromValues(List(fromLong(1), fromLong(2), fromString("3")))
    encoder.encodeUnsafe(ListSet(2, 1, 3)) shouldEqual fromValues(List(fromLong(2), fromLong(1), fromLong(3)))
    encoder.encodeUnsafe(util.Arrays.asList(1, 2, "3")) shouldEqual fromValues(
      List(fromLong(1), fromLong(2), fromString("3"))
    )
    val set = new util.LinkedHashSet[Any]
    set.add(2)
    set.add(1)
    set.add("3")
    encoder.encodeUnsafe(set) shouldEqual fromValues(List(fromLong(2), fromLong(1), fromString("3")))
  }

  it("should encode maps as a json") {
    encoder.encodeUnsafe(ListMap("key1" -> 1, "key2" -> "value")) shouldEqual
      obj("key1" -> fromLong(1), "key2" -> fromString("value"))
    val map = new util.LinkedHashMap[String, Any]()
    map.put("key1", 1)
    map.put("key2", "value")
    encoder.encodeUnsafe(map) shouldEqual obj("key1" -> fromLong(1), "key2" -> fromString("value"))
  }

  it("should encode arrays as a json") {
    encoder.encodeUnsafe(Array(1, 2, 3)) shouldEqual arr(fromLong(1), fromLong(2), fromLong(3))
    encoder.encodeUnsafe(Seq(Array(1, 2, 3))) shouldEqual arr(arr(fromLong(1), fromLong(2), fromLong(3)))
    encoder.encodeUnsafe(Array(1, "value")) shouldEqual arr(fromLong(1), fromString("value"))
  }

  it("should use custom encoders from classloader") {

    ClassLoaderWithServices.withCustomServices(
      List(
        classOf[ToJsonEncoderCustomisation] -> classOf[CustomJsonEncoderCustomisation1],
        classOf[ToJsonEncoderCustomisation] -> classOf[CustomJsonEncoderCustomisation2]
      )
    ) { classLoader =>
      val encoder = ToJsonEncoder.default

      encoder.encodeUnsafe(
        Map(
          "custom1" ->
            CustomClassToEncode(Map("custom2" -> new NestedClassToEncode))
        )
      ) shouldBe obj(
        "custom1" ->
          obj("customEncode" -> obj("custom2" -> fromString("value")))
      )
    }

  }

  it("should convert map to json and keep order of keys") {
    val map = ListMap(
      "intNumber"     -> 42,
      "floatNumber"   -> 42.42,
      "someTimestamp" -> 1496930555793L,
      "someString"    -> "hello",
      "booleanValue"  -> true
    )

    val expectedJson =
      """{"intNumber":42,"floatNumber":42.42,"someTimestamp":1496930555793,"someString":"hello","booleanValue":true}"""

    // We compare string because we want to check the order
    encoder.encodeUnsafe(map).noSpaces shouldBe expectedJson
  }

}

class CustomJsonEncoderCustomisation1 extends ToJsonEncoderCustomisation {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case CustomClassToEncode(value) =>
    obj("customEncode" -> encode(value))
  }

}

class CustomJsonEncoderCustomisation2 extends ToJsonEncoderCustomisation {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case _: NestedClassToEncode =>
    fromString("value")
  }

}

case class CustomClassToEncode(value: Any)

class NestedClassToEncode
