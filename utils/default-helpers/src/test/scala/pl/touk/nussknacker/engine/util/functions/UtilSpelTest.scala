package pl.touk.nussknacker.engine.util.functions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class UtilSpelTest extends AnyFunSuite with BaseSpelSpec with Matchers {
  test("uuid") {
    evaluate[String]("#UTIL.uuid()").length shouldBe 36
  }

  test("split") {
    evaluateAny("#UTIL.split('', ',')") shouldBe List("").asJava
    evaluateAny("#UTIL.split('1,2,,3', ',')") shouldBe List("1", "2", "", "3").asJava
    evaluateAny("#UTIL.split('1,2,,3,', ',')") shouldBe List("1", "2", "", "3", "").asJava
  }
}
