package pl.touk.nussknacker.engine.util.json

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util

import io.circe.Json._
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
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
    encoder.encode(true) shouldEqual fromBoolean(true)
    encoder.encode(LocalDateTime.of(2020, 9, 12,
      11, 55, 33, 0)) shouldEqual fromString("2020-09-12T11:55:33")
    encoder.encode(ZonedDateTime.of(2020, 9, 12,
      11, 55, 33, 0, ZoneId.of("Europe/Warsaw"))) shouldEqual fromString("2020-09-12T11:55:33+02:00")
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

  it("should encode generic record") {
    val schema =
      SchemaBuilder.builder().record("test").fields()
        .requiredString("field1")
        .requiredLong("field2").endRecord()

    val genRec = new GenericRecordBuilder(schema).set("field1", "a").set("field2", 11).build()
    encoder.encode(genRec) shouldEqual obj("field1" -> fromString("a"), "field2" -> fromLong(11))
  }

}
