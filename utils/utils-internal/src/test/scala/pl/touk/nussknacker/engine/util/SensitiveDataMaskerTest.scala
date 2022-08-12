package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class SensitiveDataMaskerTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("mask long string") {
    SensitiveDataMasker.mask("123456") shouldEqual "12**56"
  }

  test("mask short string") {
    SensitiveDataMasker.mask("1234") shouldEqual "1234"
    SensitiveDataMasker.mask("123") shouldEqual "123"
    SensitiveDataMasker.mask("12") shouldEqual "12"
    SensitiveDataMasker.mask("1") shouldEqual "1"
    SensitiveDataMasker.mask("") shouldEqual ""
  }

  test("mask json") {
    import SensitiveDataMasker._
    val inputJson = io.circe.parser.parse(
      """{
        |  "str": "123456",
        |  "obj": {
        |    "n1": "123456"
        |  },
        |  "bool": false,
        |  "null": null,
        |  "int": 123456
        |}""".stripMargin).rightValue

    val expected = """{
                     |  "str" : "12**56",
                     |  "obj" : {
                     |    "n1" : "12**56"
                     |  },
                     |  "bool" : "****",
                     |  "null" : "****",
                     |  "int" : "****"
                     |}""".stripMargin

    inputJson.masked.spaces2 shouldEqual expected
  }

}
