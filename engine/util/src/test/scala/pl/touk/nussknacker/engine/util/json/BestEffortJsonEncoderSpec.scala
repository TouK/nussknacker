package pl.touk.nussknacker.engine.util.json

import java.util

import argonaut.Argonaut._
import org.scalatest.{FunSpec, Matchers}

import scala.collection.immutable.{ListMap, ListSet}

class BestEffortJsonEncoderSpec extends FunSpec with Matchers {

  val encoder = BestEffortJsonEncoder(failOnUnkown = true)

  it("should encode simple elements as a json") {
    encoder.encode(1) shouldEqual jNumber(1)
    encoder.encode(BigDecimal.valueOf(2.0)) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encode(java.math.BigDecimal.valueOf(2.0)) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encode(2.0) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encode("ala") shouldEqual jString("ala")
  }

  it("should handle optional elements as a json") {
    encoder.encode(Some("ala")) shouldEqual jString("ala")
    encoder.encode(None) shouldEqual jNull
    encoder.encode(null) shouldEqual jNull
  }

  it("should encode collections as a json") {
    encoder.encode(List(1, 2, "3")) shouldEqual jArrayElements(jNumber(1), jNumber(2), jString("3"))
    encoder.encode(ListSet(2, 1, 3)) shouldEqual jArrayElements(jNumber(2), jNumber(1), jNumber(3))
    encoder.encode(util.Arrays.asList(1, 2, "3")) shouldEqual jArrayElements(jNumber(1), jNumber(2), jString("3"))
    val set = new util.LinkedHashSet[Any]
    set.add(2)
    set.add(1)
    set.add("3")
    encoder.encode(set) shouldEqual jArrayElements(jNumber(2), jNumber(1), jString("3"))
  }

  it("should encode maps as a json") {
    encoder.encode(ListMap("key1" -> 1, "key2" -> "value")) shouldEqual
      jObjectFields("key1" -> jNumber(1), "key2" -> jString("value"))
    val map = new util.LinkedHashMap[String, Any]()
    map.put("key1", 1)
    map.put("key2", "value")
    encoder.encode(map) shouldEqual jObjectFields("key1" -> jNumber(1), "key2" -> jString("value"))
  }

}
