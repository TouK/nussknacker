package pl.touk.nussknacker.engine.util.service

import pl.touk.nussknacker.engine.util.metrics.RateMeter


case class EspTimer(rateMeter: RateMeter, histogram: Long => Unit) {

  def update(nanoTimeStart: Long): Unit = {
    val delta = System.nanoTime() - nanoTimeStart
    rateMeter.mark()
    histogram.apply(delta)
  }
}

object EspTimer {

  val histogramSuffix = "histogram"

  val instantRateSuffix = "instantRate"

}
