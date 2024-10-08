package pl.touk.nussknacker.engine.util.functions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class Base64UtilsSpec extends AnyFunSuite with BaseSpelSpec with Matchers {

  test("encode") {
    evaluate[String]("#BASE64.encode('{\"foo\": 1}')") shouldBe "eyJmb28iOiAxfQ=="
    evaluate[String]("#BASE64.encode('')") shouldBe ""
  }

  test("decode") {
    evaluate[String]("#BASE64.decode('eyJmb28iOiAxfQ==')") shouldBe """{"foo": 1}"""
    evaluate[String]("#BASE64.decode('')") shouldBe ""
  }

  test("urlSafeEncode") {
    evaluate[String]("#BASE64.urlSafeEncode('{\"foo\": 1}')") shouldBe "eyJmb28iOiAxfQ"
    evaluate[String]("#BASE64.urlSafeEncode('')") shouldBe ""
  }

  test("urlSafeDecode") {
    evaluate[String]("#BASE64.urlSafeDecode('eyJmb28iOiAxfQ')") shouldBe """{"foo": 1}"""
    evaluate[String]("#BASE64.urlSafeDecode('')") shouldBe ""
  }

}
