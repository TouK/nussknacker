package pl.touk.nussknacker.engine.flink.api.timestampwatermark

import org.apache.flink.api.common.eventtime._
import org.apache.flink.streaming.api.datastream.DataStream

import java.time.Duration

class TimestampWatermarkHandler[T](val strategy: WatermarkStrategy[T]) {

  def assignTimestampAndWatermarks(dataStream: DataStream[T]): DataStream[T] = {
    dataStream.assignTimestampsAndWatermarks(strategy)
  }

}

object TimestampWatermarkHandler {

  trait SimpleSerializableTimestampAssigner[T] extends Serializable {

    def extractTimestamp(element: T): Long

  }

  private case class SerializableTimestampAssignerAdapter[T](simple: SimpleSerializableTimestampAssigner[T])
      extends SerializableTimestampAssigner[T] {
    override def extractTimestamp(element: T, recordTimestamp: Long): Long = simple.extractTimestamp(element)
  }

  def toAssigner[T](simple: SimpleSerializableTimestampAssigner[T]): SerializableTimestampAssigner[T] =
    SerializableTimestampAssignerAdapter(simple)

  def boundedOutOfOrderness[T](
      assigner: SimpleSerializableTimestampAssigner[T],
      maxOutOfOrderness: Duration
  ): TimestampWatermarkHandler[T] = {
    boundedOutOfOrderness(Some(toAssigner(assigner)), maxOutOfOrderness, None)
  }

  def boundedOutOfOrderness[T](
      extract: Option[SerializableTimestampAssigner[T]],
      maxOutOfOrderness: Duration,
      idlenessTimeoutDuration: Option[Duration]
  ): TimestampWatermarkHandler[T] = {
    val strategyWithLateness = WatermarkStrategy.forBoundedOutOfOrderness[T](maxOutOfOrderness)
    val strategyWithOptIdleness = idlenessTimeoutDuration match {
      case Some(duration) => strategyWithLateness.withIdleness(duration)
      case None           => strategyWithLateness
    }
    val finalStrategyWithOptAssigner = extract match {
      case Some(assigner) => strategyWithOptIdleness.withTimestampAssigner(assigner)
      case None           => strategyWithOptIdleness
    }
    new TimestampWatermarkHandler(finalStrategyWithOptAssigner)
  }

  def afterEachEvent[T](assigner: SimpleSerializableTimestampAssigner[T]): TimestampWatermarkHandler[T] = {
    afterEachEvent(toAssigner(assigner))
  }

  def afterEachEvent[T](assigner: SerializableTimestampAssigner[T]): TimestampWatermarkHandler[T] = {
    new TimestampWatermarkHandler[T](
      WatermarkStrategy
        .forGenerator((_: WatermarkGeneratorSupplier.Context) =>
          new WatermarkGenerator[T] {
            override def onEvent(event: T, eventTimestamp: Long, output: WatermarkOutput): Unit = {
              output.emitWatermark(new Watermark(eventTimestamp))
            }
            override def onPeriodicEmit(output: WatermarkOutput): Unit = {}
          }
        )
        .withTimestampAssigner(assigner)
    )
  }

}
