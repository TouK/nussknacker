package pl.touk.process.report.influxdb

import org.scalatest.{FlatSpec, Matchers}


class InfluxReporterSpec extends FlatSpec with Matchers {

  it should "correctly handle whitespaces" in {

    val counts = ProcessBaseCounts(100, nodes = Map(
      "a" -> 10,
      "a-b" -> 20,
      "a-b-c" -> 30,
      "a-b---c-d" -> 40
    ))

    val nodes = List("a", "a b", "a-b-c", "a  b - c.d")

    counts.getCountForNodeId("a") shouldBe Some(10)
    counts.getCountForNodeId("a b") shouldBe Some(20)
    counts.getCountForNodeId("a-b-c") shouldBe Some(30)
    counts.getCountForNodeId("a  b - c.d") shouldBe Some(40)

  }

}
