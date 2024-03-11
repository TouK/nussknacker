package pl.touk.nussknacker.engine.util.json

import io.circe.Json
import io.circe.Json._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.ClassLoaderWithServices

import java.time._
import java.util
import java.util.UUID
import scala.collection.immutable.{ListMap, ListSet}

class BestEffortJsonEncoderSpec extends AnyFunSpec with Matchers {

  private val encoder = BestEffortJsonEncoder.defaultForTests

  it("should encode simple elements as a json") {
    encoder.encode(1) shouldEqual fromLong(1)
    encoder.encode(BigDecimal.valueOf(2.34)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.34))
    encoder.encode(new java.math.BigDecimal("12.34")) shouldEqual fromBigDecimal(new java.math.BigDecimal("12.34"))
    encoder.encode(BigDecimal("12.34")) shouldEqual fromBigDecimal(BigDecimal("12.34"))
    encoder.encode(new java.math.BigInteger("1234")) shouldEqual fromBigInt(new java.math.BigInteger("1234"))
    encoder.encode(BigInt("1234")) shouldEqual fromBigInt(BigInt("1234"))
    encoder.encode(12.34f) shouldEqual fromFloatOrNull(12.34f)
    encoder.encode(java.math.BigDecimal.valueOf(2.0)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode(2.0) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode("ala") shouldEqual fromString("ala")
    encoder.encode(true) shouldEqual fromBoolean(true)
    encoder.encode(java.lang.Boolean.TRUE) shouldEqual fromBoolean(true)
    encoder.encode(LocalDateTime.of(2020, 9, 12, 11, 55, 33, 0)) shouldEqual fromString("2020-09-12T11:55:33")

    encoder.encode(LocalDate.of(2020, 9, 12)) shouldEqual fromString("2020-09-12")
    encoder.encode(LocalTime.of(11, 55, 33)) shouldEqual fromString("11:55:33")

    val zonedTime = ZonedDateTime.of(2020, 9, 12, 11, 55, 33, 0, ZoneId.of("Europe/Warsaw"))
    encoder.encode(zonedTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    encoder.encode(zonedTime.toOffsetDateTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    // Default Instant encoding is in Z
    encoder.encode(zonedTime.toInstant) shouldEqual fromString("2020-09-12T09:55:33Z")

    val uuid = UUID.randomUUID()
    encoder.encode(uuid) shouldEqual fromString(uuid.toString)
    encoder.encode(SampleEnum.LOREM) shouldEqual fromString("LOREM")
  }

  it("should handle optional elements as a json") {
    encoder.encode(Some("ala")) shouldEqual fromString("ala")
    encoder.encode(None) shouldEqual Null
    encoder.encode(null) shouldEqual Null
  }

  it("should encode collections as a json") {
    encoder.encode(List(1, 2, "3")) shouldEqual fromValues(List(fromLong(1), fromLong(2), fromString("3")))
    encoder.encode(ListSet(2, 1, 3)) shouldEqual fromValues(List(fromLong(2), fromLong(1), fromLong(3)))
    encoder.encode(util.Arrays.asList(1, 2, "3")) shouldEqual fromValues(
      List(fromLong(1), fromLong(2), fromString("3"))
    )
    val set = new util.LinkedHashSet[Any]
    set.add(2)
    set.add(1)
    set.add("3")
    encoder.encode(set) shouldEqual fromValues(List(fromLong(2), fromLong(1), fromString("3")))
  }

  it("should encode maps as a json") {
    encoder.encode(ListMap("key1" -> 1, "key2" -> "value")) shouldEqual
      obj("key1" -> fromLong(1), "key2" -> fromString("value"))
    val map = new util.LinkedHashMap[String, Any]()
    map.put("key1", 1)
    map.put("key2", "value")
    encoder.encode(map) shouldEqual obj("key1" -> fromLong(1), "key2" -> fromString("value"))
  }

  it("should encode maps with keys which are already maps as a json") {
    val map1: util.Map[String, Any] = new util.HashMap[String, Any]()
    map1.put("key1", "value1")
    map1.put("key2", 2)

    val map2: util.Map[String, Any] = new util.HashMap[String, Any]()
    map2.put("key3", "value3")
    map2.put("key4", 4)
    val m: util.HashMap[util.Map[String, Any], util.Map[String, Any]] =
      new util.HashMap[util.Map[String, Any], util.Map[String, Any]]()
    m.put(map1, map2)

    encoder.encode(m) shouldEqual obj(
      "{\"key1\":\"value1\",\"key2\":2}" -> obj("key3" -> fromString("value3"), "key4" -> fromInt(4))
    )
  }

  it("should encode arrays as a json") {
    encoder.encode(Array(1, 2, 3)) shouldEqual arr(fromLong(1), fromLong(2), fromLong(3))
    encoder.encode(Seq(Array(1, 2, 3))) shouldEqual arr(arr(fromLong(1), fromLong(2), fromLong(3)))
    encoder.encode(Array(1, "value")) shouldEqual arr(fromLong(1), fromString("value"))
  }

  it("should use custom encoders from classloader") {

    ClassLoaderWithServices.withCustomServices(
      List(classOf[ToJsonEncoder] -> classOf[CustomJsonEncoder1], classOf[ToJsonEncoder] -> classOf[CustomJsonEncoder2])
    ) { classLoader =>
      val encoder = BestEffortJsonEncoder(failOnUnknown = true, classLoader)

      encoder.encode(
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

}

class CustomJsonEncoder1 extends ToJsonEncoder {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case CustomClassToEncode(value) =>
    obj("customEncode" -> encode(value))
  }

}

class CustomJsonEncoder2 extends ToJsonEncoder {

  override def encoder(encode: Any => Json): PartialFunction[Any, Json] = { case _: NestedClassToEncode =>
    fromString("value")
  }

}

case class CustomClassToEncode(value: Any)

class NestedClassToEncode
