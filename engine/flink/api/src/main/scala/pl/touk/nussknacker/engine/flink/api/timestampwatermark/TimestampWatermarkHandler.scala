package pl.touk.nussknacker.engine.flink.api.timestampwatermark

import java.time.Duration

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.scala.DataStream

import scala.annotation.nowarn

trait TimestampWatermarkHandler[T] extends Serializable {

  def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T]

  // this timestamp extraction is supported for legacy handlers
  def extractTimestamp(element: T, recordTimestamp: Long): Option[Long]

}

class StandardTimestampWatermarkHandler[T](strategy: WatermarkStrategy[T]) extends TimestampWatermarkHandler[T] {

  override def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T] = {
    dataStream.assignTimestampsAndWatermarks(strategy)
  }

  def extractTimestamp(element: T, recordTimestamp: Long): Option[Long] = None
}

object StandardTimestampWatermarkHandler {

  def timestampAssigner[T](extract: T => Long): SerializableTimestampAssigner[T] = new SerializableTimestampAssigner[T] {
    override def extractTimestamp(element: T, recordTimestamp: Long): Long = extract(element)
  }

  def boundedOutOfOrderness[T](extract: T => Long, maxOutOfOrderness: Duration): TimestampWatermarkHandler[T] = {
    new StandardTimestampWatermarkHandler(WatermarkStrategy.forBoundedOutOfOrderness(maxOutOfOrderness).withTimestampAssigner(timestampAssigner(extract)))
  }

}

@silent("deprecated")
@nowarn("cat=deprecation")
class LegacyTimestampWatermarkHandler[T](timestampAssigner: TimestampAssigner[T]) extends TimestampWatermarkHandler[T] {
  override def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T] = {
    timestampAssigner match {
      case periodic: AssignerWithPeriodicWatermarks[T@unchecked] =>
        dataStream.assignTimestampsAndWatermarks(periodic)
      case punctuated: AssignerWithPunctuatedWatermarks[T@unchecked] =>
        dataStream.assignTimestampsAndWatermarks(punctuated)
    }
  }

  override def extractTimestamp(element: T, recordTimestamp: Long): Option[Long] =
    Option(timestampAssigner.extractTimestamp(element, recordTimestamp))
}
