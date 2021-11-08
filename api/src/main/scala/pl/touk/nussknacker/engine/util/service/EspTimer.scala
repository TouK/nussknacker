package pl.touk.nussknacker.engine.util.service

import pl.touk.nussknacker.engine.util.metrics.{Histogram, RateMeter}


case class EspTimer(rateMeter: RateMeter, histogram: Histogram) {

  def update(nanoTimeStart: Long): Unit = {
    val delta = System.nanoTime() - nanoTimeStart
    rateMeter.mark()
    histogram.update(delta)
  }
}

object EspTimer {

  val histogramSuffix = "histogram"

  val instantRateSuffix = "instantRate"

}
