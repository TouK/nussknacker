package pl.touk.nussknacker.engine.flink.api.timestampwatermark

import org.apache.flink.api.common.eventtime._

import java.time.Duration

object WatermarkStrategyUtils {

  def boundedOutOfOrderness[T](
      assigner: SerializableTimestampAssigner[T],
      maxOutOfOrderness: Duration
  ): WatermarkStrategy[T] = {
    boundedOutOfOrderness(Some(assigner), maxOutOfOrderness, None)
  }

  def boundedOutOfOrderness[T](
      extract: Option[SerializableTimestampAssigner[T]],
      maxOutOfOrderness: Duration,
      idlenessTimeoutDuration: Option[Duration]
  ): WatermarkStrategy[T] = {
    val strategyWithLateness = WatermarkStrategy.forBoundedOutOfOrderness[T](maxOutOfOrderness)
    val strategyWithOptIdleness = idlenessTimeoutDuration match {
      case Some(duration) => strategyWithLateness.withIdleness(duration)
      case None           => strategyWithLateness
    }
    extract match {
      case Some(assigner) => strategyWithOptIdleness.withTimestampAssigner(assigner)
      case None           => strategyWithOptIdleness
    }
  }

  def afterEachEvent[T](
      assigner: SerializableTimestampAssigner[T],
      maxOutOfOrderness: Duration = Duration.ZERO
  ): WatermarkStrategy[T] = {
    WatermarkStrategy
      .forGenerator((_: WatermarkGeneratorSupplier.Context) =>
        new WatermarkGenerator[T] {
          override def onEvent(event: T, eventTimestamp: Long, output: WatermarkOutput): Unit = {
            output.emitWatermark(new Watermark(eventTimestamp - maxOutOfOrderness.toMillis))
          }
          override def onPeriodicEmit(output: WatermarkOutput): Unit = {}
        }
      )
      .withTimestampAssigner(assigner)
  }

}
