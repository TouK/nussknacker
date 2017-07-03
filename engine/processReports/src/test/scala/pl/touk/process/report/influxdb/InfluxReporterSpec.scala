package pl.touk.process.report.influxdb

import org.scalatest.{FlatSpec, Matchers}


class InfluxReporterSpec extends FlatSpec with Matchers {

  it should "correctly handle whitespaces" in {

    val counts = ProcessBaseCounts(100, ends = Map(), deadEnds = Map(), nodes = Map(
      "a" -> 10,
      "a-b" -> 20,
      "a-b-c" -> 30,
      "a-b---c-d" -> 40
    ))

    val nodes = List("a", "a b", "a-b-c", "a  b - c.d")

    counts.mapToOriginalNodeIds(nodes).nodes shouldBe Map(
      "a" -> 10,
      "a b" -> 20,
      "a-b-c" -> 30,
      "a  b - c.d" -> 40
    )

  }

}
