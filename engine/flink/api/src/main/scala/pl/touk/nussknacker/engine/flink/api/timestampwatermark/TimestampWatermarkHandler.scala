package pl.touk.nussknacker.engine.flink.api.timestampwatermark

import java.time.Duration
import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, TimestampAssignerSupplier, Watermark, WatermarkGenerator, WatermarkGeneratorSupplier, WatermarkOutput, WatermarkStrategy}
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

  //cannot use scala function as lambda, as it is not serializable...
  def timestampAssigner[T](extract: T => Long): SerializableTimestampAssigner[T] = new SerializableTimestampAssigner[T] {
    override def extractTimestamp(element: T, recordTimestamp: Long): Long = extract(element)
  }

  //cannot use scala function as lambda, as it is not serializable...
  def timestampAssignerWithTimestamp[T](extract: (T, Long) => Long): SerializableTimestampAssigner[T] = new SerializableTimestampAssigner[T] {
    override def extractTimestamp(element: T, recordTimestamp: Long): Long = extract(element, recordTimestamp)
  }

  def boundedOutOfOrderness[T](extract: T => Long, maxOutOfOrderness: Duration): TimestampWatermarkHandler[T] = {
    new StandardTimestampWatermarkHandler(WatermarkStrategy.forBoundedOutOfOrderness(maxOutOfOrderness).withTimestampAssigner(timestampAssigner(extract)))
  }

  def afterEachEvent[T](extract: T => Long): TimestampWatermarkHandler[T] = {
    new StandardTimestampWatermarkHandler[T](WatermarkStrategy.forGenerator((_: WatermarkGeneratorSupplier.Context) => new WatermarkGenerator[T] {
      override def onEvent(event: T, eventTimestamp: Long, output: WatermarkOutput): Unit = {
        output.emitWatermark(new Watermark(eventTimestamp))
      }
      override def onPeriodicEmit(output: WatermarkOutput): Unit = {}
    }).withTimestampAssigner(timestampAssigner(extract)))
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
