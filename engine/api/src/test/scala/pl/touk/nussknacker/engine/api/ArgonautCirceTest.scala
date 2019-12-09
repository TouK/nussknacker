package pl.touk.nussknacker.engine.api

import org.scalatest.{FunSuite, Matchers}
import argonaut.Argonaut._
import argonaut.{Json => AJson}
import io.circe.{Json => CJson}

class ArgonautCirceTest extends FunSuite with Matchers {

  test("Long value argonaut to circe encoding") {
    val beforeEncoding: Long = 1

    val ajson: AJson = beforeEncoding.asJson
    val cjson: CJson = ArgonautCirce.toCirce(ajson)

    cjson.asNumber.get.toLong.get shouldBe beforeEncoding
  }

  test("Big decimal value argonaut to circe encoding") {
    val beforeEncoding: BigDecimal = 1.1

    val ajson: AJson = beforeEncoding.asJson
    val cjson: CJson = ArgonautCirce.toCirce(ajson)

    cjson.asNumber.get.toBigDecimal.get shouldBe beforeEncoding
  }

  test("Double value argonaut to circe encoding") {
    val beforeEncoding: Double = 22.22

    val ajson: AJson = beforeEncoding.asJson
    val cjson: CJson = ArgonautCirce.toCirce(ajson)

    cjson.asNumber.get.toDouble shouldBe beforeEncoding
  }

}
