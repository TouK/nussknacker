package pl.touk.nussknacker.engine.util.json

import java.util

import argonaut.Argonaut._
import org.scalatest.{FunSpec, Matchers}

import scala.collection.immutable.{ListMap, ListSet}

class BestEffortJsonEncoderSpec extends FunSpec with Matchers {

  val encoder = BestEffortJsonEncoder(failOnUnkown = true)

  it("should encode simple elements as a json") {
    encoder.encodeToArgonaut(1) shouldEqual jNumber(1)
    encoder.encodeToArgonaut(BigDecimal.valueOf(2.0)) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encodeToArgonaut(java.math.BigDecimal.valueOf(2.0)) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encodeToArgonaut(2.0) shouldEqual jNumber(BigDecimal.valueOf(2.0))
    encoder.encodeToArgonaut("ala") shouldEqual jString("ala")
  }

  it("should handle optional elements as a json") {
    encoder.encodeToArgonaut(Some("ala")) shouldEqual jString("ala")
    encoder.encodeToArgonaut(None) shouldEqual jNull
    encoder.encodeToArgonaut(null) shouldEqual jNull
  }

  it("should encode collections as a json") {
    encoder.encodeToArgonaut(List(1, 2, "3")) shouldEqual jArrayElements(jNumber(1), jNumber(2), jString("3"))
    encoder.encodeToArgonaut(ListSet(2, 1, 3)) shouldEqual jArrayElements(jNumber(2), jNumber(1), jNumber(3))
    encoder.encodeToArgonaut(util.Arrays.asList(1, 2, "3")) shouldEqual jArrayElements(jNumber(1), jNumber(2), jString("3"))
    val set = new util.LinkedHashSet[Any]
    set.add(2)
    set.add(1)
    set.add("3")
    encoder.encodeToArgonaut(set) shouldEqual jArrayElements(jNumber(2), jNumber(1), jString("3"))
  }

  it("should encode maps as a json") {
    encoder.encodeToArgonaut(ListMap("key1" -> 1, "key2" -> "value")) shouldEqual
      jObjectFields("key1" -> jNumber(1), "key2" -> jString("value"))
    val map = new util.LinkedHashMap[String, Any]()
    map.put("key1", 1)
    map.put("key2", "value")
    encoder.encodeToArgonaut(map) shouldEqual jObjectFields("key1" -> jNumber(1), "key2" -> jString("value"))
  }

}
