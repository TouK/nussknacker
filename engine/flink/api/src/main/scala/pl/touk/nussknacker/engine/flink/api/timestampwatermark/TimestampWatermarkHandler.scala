package pl.touk.nussknacker.engine.flink.api.timestampwatermark

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime._
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.scala.DataStream

import java.time.Duration
import scala.annotation.nowarn

sealed trait TimestampWatermarkHandler[T] extends Serializable {

  def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T]

}

class StandardTimestampWatermarkHandler[T](val strategy: WatermarkStrategy[T]) extends TimestampWatermarkHandler[T] {

  override def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T] = {
    dataStream.assignTimestampsAndWatermarks(strategy)
  }

}

object StandardTimestampWatermarkHandler {

  trait SimpleSerializableTimestampAssigner[T] extends Serializable {

    def extractTimestamp(element: T): Long

  }

  private case class SerializableTimestampAssignerAdapter[T](simple: SimpleSerializableTimestampAssigner[T]) extends SerializableTimestampAssigner[T] {
    override def extractTimestamp(element: T, recordTimestamp: Long): Long = simple.extractTimestamp(element)
  }

  def toAssigner[T](simple: SimpleSerializableTimestampAssigner[T]): SerializableTimestampAssigner[T] =
    SerializableTimestampAssignerAdapter(simple)

  def boundedOutOfOrderness[T](assigner: SimpleSerializableTimestampAssigner[T], maxOutOfOrderness: Duration): TimestampWatermarkHandler[T] = {
    boundedOutOfOrderness(toAssigner(assigner), maxOutOfOrderness)
  }

  def boundedOutOfOrderness[T](extract: SerializableTimestampAssigner[T], maxOutOfOrderness: Duration): TimestampWatermarkHandler[T] = {
    new StandardTimestampWatermarkHandler(WatermarkStrategy.forBoundedOutOfOrderness(maxOutOfOrderness).withTimestampAssigner(extract))
  }

  def afterEachEvent[T](assigner: SimpleSerializableTimestampAssigner[T]): TimestampWatermarkHandler[T] = {
    afterEachEvent(toAssigner(assigner))
  }

  def afterEachEvent[T](assigner: SerializableTimestampAssigner[T]): TimestampWatermarkHandler[T] = {
    new StandardTimestampWatermarkHandler[T](WatermarkStrategy.forGenerator((_: WatermarkGeneratorSupplier.Context) => new WatermarkGenerator[T] {
      override def onEvent(event: T, eventTimestamp: Long, output: WatermarkOutput): Unit = {
        output.emitWatermark(new Watermark(eventTimestamp))
      }
      override def onPeriodicEmit(output: WatermarkOutput): Unit = {}
    }).withTimestampAssigner(assigner))
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

  def extractTimestamp(element: T, recordTimestamp: Long): Long =
    timestampAssigner.extractTimestamp(element, recordTimestamp)
}
