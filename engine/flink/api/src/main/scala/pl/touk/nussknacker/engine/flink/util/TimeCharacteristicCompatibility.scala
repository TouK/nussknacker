package pl.touk.nussknacker.engine.flink.util

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.annotation.nowarn

//Setting time characteristic is not needed and deprecated in Flink >= 1.12. We use this class not to pollute codebase
//with deprecation warnings, while remaining compatible with older Flink versions
// Remove @silent after upgrade to silencer 1.7
@silent("deprecated")
@nowarn("deprecated")
object TimeCharacteristicCompatibility {

  def useEventTime(env: StreamExecutionEnvironment): Unit = env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

  def useIngestionTime(env: StreamExecutionEnvironment): Unit = env.setStreamTimeCharacteristic(TimeCharacteristic.IngestionTime)

  def defineCharacteristicByAssigner(env: StreamExecutionEnvironment, handler: Option[TimestampWatermarkHandler[_]]): Unit = {
    env.setStreamTimeCharacteristic(if (handler.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)
  }
}
