package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object DailyWindowsOffsetDependingOnTimezone {

  def offset(windowDuration: Duration, dailyWindowsAlignZoneId: ZoneId): Option[Duration] =
    if (DailyWindowDetector.isDailyWindow(windowDuration)) {
      val offsetInMillis = TimeZone.getTimeZone(dailyWindowsAlignZoneId).getRawOffset * -1
      Some(Duration.apply(offsetInMillis, TimeUnit.MILLISECONDS))
    } else {
      None
    }

}

object DailyWindowDetector {
  def isDailyWindow(windowDuration: Duration): Boolean = windowDuration.gteq(Duration(1, TimeUnit.DAYS)) &&
    Duration(windowDuration.toDays, TimeUnit.DAYS) == windowDuration
}