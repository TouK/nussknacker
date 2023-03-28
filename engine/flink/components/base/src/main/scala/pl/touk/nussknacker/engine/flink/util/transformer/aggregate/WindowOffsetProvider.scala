package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.scalalogging.LazyLogging

import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait WindowOffsetProvider {
  def offset(windowDuration: Duration): Duration
}

object WindowOffsetProvider extends LazyLogging {

  private final val FlinkDailyWindowTimezoneIdEnvName = "FLINK_DAILY_WINDOW_TIMEZONE_ID"

  val DailyWindowsOffsetDependingOnTimezone: WindowOffsetProvider = (windowDuration: Duration) =>
    if (DailyWindowDetector.isDailyWindow(windowDuration)) {
      val offsetInMillis = TimeZone.getTimeZone(getTimezoneId).getRawOffset * -1
      Duration.apply(offsetInMillis, TimeUnit.MILLISECONDS)
    } else {
      Duration.Zero
    }

  private def getTimezoneId = {
    Option(System.getenv(FlinkDailyWindowTimezoneIdEnvName)).map(zoneId =>
      Try(ZoneId.of(zoneId)) match {
        case Failure(exception) =>
          logger.warn(s"Got invalid value: $FlinkDailyWindowTimezoneIdEnvName: $zoneId, using system default: ${ZoneId.systemDefault()}", exception)
          ZoneId.systemDefault()
        case Success(value) => value
      }).getOrElse(ZoneId.systemDefault())
  }

}

object DailyWindowDetector {
  def isDailyWindow(windowDuration: Duration): Boolean = windowDuration.gteq(Duration(1, TimeUnit.DAYS)) &&
    Duration(windowDuration.toDays, TimeUnit.DAYS) == windowDuration
}