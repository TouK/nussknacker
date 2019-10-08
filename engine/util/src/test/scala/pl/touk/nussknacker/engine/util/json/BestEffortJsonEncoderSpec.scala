package pl.touk.nussknacker.engine.util.json

import java.util

import io.circe.Json._
import org.scalatest.{FunSpec, Matchers}

import scala.collection.immutable.{ListMap, ListSet}

class BestEffortJsonEncoderSpec extends FunSpec with Matchers {

  val encoder = BestEffortJsonEncoder(failOnUnkown = true)

  it("should encode simple elements as a json") {
    encoder.encode(1) shouldEqual fromLong(1)
    encoder.encode(BigDecimal.valueOf(2.0)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode(java.math.BigDecimal.valueOf(2.0)) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode(2.0) shouldEqual fromBigDecimal(BigDecimal.valueOf(2.0))
    encoder.encode("ala") shouldEqual fromString("ala")
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

}
