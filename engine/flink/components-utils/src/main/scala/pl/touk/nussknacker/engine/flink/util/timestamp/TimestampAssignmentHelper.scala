package pl.touk.nussknacker.engine.flink.util.timestamp

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import java.util

class TimestampAssignmentHelper[T: TypeInformation](timestampAssigner: TimestampWatermarkHandler[TimestampedValue[T]]) {

  def assignWatermarks(stream: DataStream[T]): DataStream[T] = {
    val valueTypeInfo: TypeInformation[T] = implicitly[TypeInformation[T]]

    val timestampAssignmentHelperType: TypeInformation[TimestampedValue[T]] = Types.POJO(
      classOf[TimestampedValue[T]],
      util.Map.of(
        "timestamp",
        Types.LONG,
        "hasTimestamp", // TODO: Should we mark this field?
        Types.BOOLEAN,
        "value",
        valueTypeInfo
      )
    )

    val timestampedStream = stream
      .process(
        (value: T, ctx: ProcessFunction[T, TimestampedValue[T]]#Context, out: Collector[TimestampedValue[T]]) =>
          out.collect(new TimestampedValue(value, ctx.timestamp())),
        timestampAssignmentHelperType
      )

    timestampAssigner
      .assignTimestampAndWatermarks(timestampedStream)
      .map(
        (tv: TimestampedValue[T]) => tv.getValue,
        valueTypeInfo
      )
  }

}
