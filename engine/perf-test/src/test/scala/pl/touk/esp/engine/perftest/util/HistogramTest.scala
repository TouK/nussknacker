package pl.touk.esp.engine.perftest.util

import org.scalatest.{FlatSpec, Matchers}

class HistogramTest extends FlatSpec with Matchers {

  it should "compute mean correctly" in {
    Histogram.empty[Int].withValues(0).mean shouldEqual 0
    Histogram.empty[Int].withValues(0, 1).mean shouldEqual 0
    Histogram.empty[Int].withValues(0, 1, 2).mean shouldEqual 1
    Histogram.empty[Int].withValues(0, 1, 2, 3).mean shouldEqual 1
    Histogram.empty[Int].withValues(0, 1, 2, 3, 4).mean shouldEqual 2
  }

  it should "compute corner percentiles correctly" in {
    Histogram.empty[Int].withValues(0).percentile(0.1) shouldEqual 0
    Histogram.empty[Int].withValues(0, 1).percentile(0.1) shouldEqual 0
    Histogram.empty[Int].withValues(0).percentile(99.9) shouldEqual 0
    Histogram.empty[Int].withValues(0, 1).percentile(99.9) shouldEqual 1
  }

}
