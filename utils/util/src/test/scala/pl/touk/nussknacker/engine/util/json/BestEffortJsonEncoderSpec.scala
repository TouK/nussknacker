package pl.touk.nussknacker.engine.util.json

import io.circe.Json
import io.circe.Json._
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.test.ClassLoaderWithServices

import java.time._
import java.util
import java.util.UUID
import scala.collection.immutable.{ListMap, ListSet}

class BestEffortJsonEncoderSpec extends FunSpec with Matchers {

  private val encoder = BestEffortJsonEncoder.defaultForTests

  it("should encode simple elements as a json") {
    encoder.encode(1) shouldEqual fromLong(1)
    encoder.encode(BigDecimal.valueOf(2.34)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.34))
    encoder.encode(new java.math.BigDecimal("12.34")) shouldEqual fromBigDecimal(new java.math.BigDecimal("12.34"))
    encoder.encode(new java.math.BigInteger("1234")) shouldEqual fromBigInt(new java.math.BigInteger("1234"))
    encoder.encode(12.34f) shouldEqual fromFloatOrNull(12.34f)
    encoder.encode(java.math.BigDecimal.valueOf(2.0)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode(2.0) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode("ala") shouldEqual fromString("ala")
    encoder.encode(true) shouldEqual fromBoolean(true)
    encoder.encode(LocalDateTime.of(2020, 9, 12,
      11, 55, 33, 0)) shouldEqual fromString("2020-09-12T11:55:33")

    encoder.encode(LocalDate.of(2020, 9, 12)) shouldEqual fromString("2020-09-12")
    encoder.encode(LocalTime.of(11, 55, 33)) shouldEqual fromString("11:55:33")


    val zonedTime = ZonedDateTime.of(2020, 9, 12,
          11, 55, 33, 0, ZoneId.of("Europe/Warsaw"))
    encoder.encode(zonedTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    encoder.encode(zonedTime.toOffsetDateTime) shouldEqual fromString("2020-09-12T11:55:33+02:00")
    //Default Instant encoding is in Z
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
    encoder.encode(util.Arrays.asList(1, 2, "3")) shouldEqual fromValues(List(fromLong(1), fromLong(2), fromString("3")))
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

  it("should use custom encoders from classloader") {

    ClassLoaderWithServices.withCustomServices(List(classOf[ToJsonEncoder] -> classOf[CustomJsonEncoder1],
      classOf[ToJsonEncoder] -> classOf[CustomJsonEncoder2])) { classLoader =>
      val encoder = BestEffortJsonEncoder(failOnUnkown = true, classLoader)

      encoder.encode(Map("custom1" ->
        CustomClassToEncode(Map("custom2" -> new NestedClassToEncode)))) shouldBe obj("custom1" ->
          obj("customEncode" -> obj("custom2" -> fromString("value"))))
    }

  }

}

class CustomJsonEncoder1 extends ToJsonEncoder {

  override def encoder(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json] = {
    case CustomClassToEncode(value) => obj("customEncode" -> encoder.encode(value))
  }
}

class CustomJsonEncoder2 extends ToJsonEncoder {

  override def encoder(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json] = {
    case _: NestedClassToEncode => fromString("value")
  }
}

case class CustomClassToEncode(value: Any)

class NestedClassToEncode
