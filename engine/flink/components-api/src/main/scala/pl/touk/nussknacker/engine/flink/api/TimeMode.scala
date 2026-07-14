package pl.touk.nussknacker.engine.flink.api

import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction

import java.time.Instant

sealed trait TimeMode {
  def label: String
}

object TimeMode {
  case object EventTime      extends TimeMode { val label = "Event time"      }
  case object ProcessingTime extends TimeMode { val label = "Processing time" }

  val values: List[TimeMode] = List(EventTime, ProcessingTime)

  def fromName(name: String): TimeMode =
    values
      .find(_.toString == name)
      .getOrElse(
        throw new IllegalArgumentException(s"Unknown time mode: '$name'. Expected one of: ${values.mkString(", ")}")
      )

  implicit val timeModeReader: ValueReader[TimeMode] = (config, path) => fromName(config.getString(path))

}

/**
  * Mix into a KeyedProcessFunction to resolve the current time and register timers according to the chosen TimeMode.
  */
trait WithTimeMode[K, IN, OUT] extends LazyLogging { self: KeyedProcessFunction[K, IN, OUT] =>

  protected type FlinkTimerCtx = KeyedProcessFunction[K, IN, OUT]#OnTimerContext

  protected type FlinkCtx = KeyedProcessFunction[K, IN, OUT]#Context

  def timeMode: TimeMode

  protected def currentTime(ctx: FlinkCtx): Long = timeMode match {
    case TimeMode.EventTime      => ctx.timestamp()
    case TimeMode.ProcessingTime => ctx.timerService().currentProcessingTime()
  }

  protected def registerTimer(timerService: TimerService, fireTime: Long): Unit = {
    logger.trace(s"Registering timer: $timeMode, will fire on ${Instant.ofEpochMilli(fireTime)} ($fireTime)")
    timeMode match {
      case TimeMode.EventTime      => timerService.registerEventTimeTimer(fireTime)
      case TimeMode.ProcessingTime => timerService.registerProcessingTimeTimer(fireTime)
    }
  }

}
