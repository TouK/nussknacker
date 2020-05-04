package pl.touk.nussknacker.processCounts.influxdb

import org.scalatest.{FunSuite, Matchers}


class InfluxReporterSpec extends FunSuite with Matchers {

  test("correctly handle whitespaces when whitespaces are mapped to dashes") {

    val counts = ProcessBaseCounts(nodes = Map(
      "a" -> 10,
      "a-b" -> 20,
      "a-b-c" -> 30,
      "a-b---c-d" -> 40
    ))

    counts.getCountForNodeId("a") shouldBe Some(10)
    counts.getCountForNodeId("a b") shouldBe Some(20)
    counts.getCountForNodeId("a-b-c") shouldBe Some(30)
    counts.getCountForNodeId("a  b - c.d") shouldBe Some(40)

  }

  test("correctly handle whitespaces when whitespaces are just regular whitespaces") {

    val counts = ProcessBaseCounts(nodes = Map(
      "a" -> 10,
      "a b" -> 20,
      "a b c" -> 30,
      "a  b - c-d" -> 40
    ))

    counts.getCountForNodeId("a") shouldBe Some(10)
    counts.getCountForNodeId("a b") shouldBe Some(20)
    counts.getCountForNodeId("a b c") shouldBe Some(30)
    counts.getCountForNodeId("a  b - c.d") shouldBe Some(40)

  }

}
