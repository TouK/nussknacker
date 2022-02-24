package pl.touk.nussknacker.engine.flink.util.timestamp

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions._
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

class TimestampAssignmentHelper[T: TypeInformation](timestampAssigner: TimestampWatermarkHandler[TimestampedValue[T]]) {

  def assignWatermarks(stream: DataStream[T]): DataStream[T] = {
    val timestampedStream = stream
      .process(new ProcessFunction[T, TimestampedValue[T]] {
        override def processElement(value: T,
                                    ctx: ProcessFunction[T, TimestampedValue[T]]#Context,
                                    out: Collector[TimestampedValue[T]]): Unit =
          out.collect(new TimestampedValue(value, ctx.timestamp()))
      })

    val withTimestampAssigner = timestampAssigner.assignTimestampAndWatermarks(timestampedStream)
    withTimestampAssigner.map((tv: TimestampedValue[T]) => tv.getValue)
  }

}
