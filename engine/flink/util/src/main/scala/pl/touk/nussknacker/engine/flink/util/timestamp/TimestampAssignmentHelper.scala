package pl.touk.nussknacker.engine.flink.util.timestamp

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions._
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector

class TimestampAssignmentHelper[T: TypeInformation](timestampAssigner: TimestampAssigner[TimestampedValue[T]]) {

  def assignWatermarks(stream: DataStream[T]): DataStream[T] = {
    val timestampedStream = stream
      .process(new ProcessFunction[T, TimestampedValue[T]] {
        override def processElement(value: T,
                                    ctx: ProcessFunction[T, TimestampedValue[T]]#Context,
                                    out: Collector[TimestampedValue[T]]): Unit =
          out.collect(new TimestampedValue(value, ctx.timestamp()))
      })

    val withTimestampAssigner = timestampAssigner match {
      case periodic: AssignerWithPeriodicWatermarks[TimestampedValue[T]@unchecked] =>
        timestampedStream.assignTimestampsAndWatermarks(periodic)
      case punctuated: AssignerWithPunctuatedWatermarks[TimestampedValue[T]@unchecked] =>
        timestampedStream.assignTimestampsAndWatermarks(punctuated)
    }

    withTimestampAssigner.map((tv: TimestampedValue[T]) => tv.getValue)
  }

}
