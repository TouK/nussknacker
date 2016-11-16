package pl.touk.esp.engine.flink.util.metrics

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

import org.apache.flink.metrics.Gauge

//To jest bardzo slaba implementacja, ale na nasze potrzeby moze wystarczy...
class InstantRateMeter extends Gauge[Double] {

  val counter = new LongAdder
  private val NANOS_IN_SECOND = TimeUnit.SECONDS.toNanos(1)
  private val TICK_INTERVAL = TimeUnit.SECONDS.toNanos(1)
  var lastTick = System.nanoTime()

  var lastValue = 0d

  def mark(): Unit = {
    counter.add(1)
  }

  override def getValue = synchronized {
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

