package pl.touk.nussknacker.engine.livedata

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong

class MutableClock(initialTime: Instant) extends Clock {

  private val epochSecond =
    new AtomicLong(initialTime.getEpochSecond)

  def advanceBySeconds(seconds: Long): Unit =
    epochSecond.getAndUpdate(_ + seconds)

  def setTime(newInstant: Instant): Unit =
    epochSecond.set(newInstant.getEpochSecond)

  override def getZone: ZoneId = ZoneId.systemDefault()

  override def withZone(zone: ZoneId): Clock = this

  override def instant(): Instant = Instant.ofEpochSecond(epochSecond.get())

}
