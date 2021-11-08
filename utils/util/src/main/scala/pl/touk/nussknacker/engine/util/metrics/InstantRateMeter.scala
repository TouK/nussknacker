package pl.touk.nussknacker.engine.util.metrics

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

//this is poor implementation, but should be ok for our needs
class InstantRateMeter extends RateMeter with Gauge[Double] {

  val counter = new LongAdder
  private val NANOS_IN_SECOND = TimeUnit.SECONDS.toNanos(1)
  private val TICK_INTERVAL = TimeUnit.SECONDS.toNanos(1)
  var lastTick: Long = System.nanoTime()

  var lastValue = 0d

  override def mark(): Unit = {
    counter.add(1)
  }

  override def getValue: Double = synchronized {
    val previousTick = lastTick
    val currentTime = System.nanoTime()
    val timeFromLast = currentTime - previousTick
    if (timeFromLast > TICK_INTERVAL) {
      lastTick = currentTime
      val count = counter.sumThenReset()
      lastValue = NANOS_IN_SECOND * count.toDouble / timeFromLast
    }
    lastValue
  }

}
