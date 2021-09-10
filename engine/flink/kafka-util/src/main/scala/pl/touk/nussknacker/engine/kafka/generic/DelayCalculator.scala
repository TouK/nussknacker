package pl.touk.nussknacker.engine.kafka.generic

import java.time.{Instant, LocalDateTime, ZoneId}

trait DelayCalculator {
  def calculateDelay(currentTime: Long, eventTime: Long): Long
}

class FixedDelayProvider(delayInMillis: Long) extends DelayCalculator {
  override def calculateDelay(currentTime: Long, eventTime: Long): Long = {
    val eventLatency = currentTime - eventTime
    delayInMillis - eventLatency
  }
}

class ScheduleDelayProvider(schedule: Schedule) extends DelayCalculator {
  override def calculateDelay(currentTime: Long, eventTime: Long): Long = {
    schedule.nextAfter(LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTime), ZoneId.systemDefault()))
      .atZone(ZoneId.systemDefault()).toInstant.toEpochMilli - currentTime
  }
}