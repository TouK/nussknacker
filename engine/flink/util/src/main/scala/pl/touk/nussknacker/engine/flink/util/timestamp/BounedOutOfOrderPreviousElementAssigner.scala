package pl.touk.nussknacker.engine.flink.util.timestamp


import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.watermark.Watermark

/**
 * It is a copy-paste of BoundedOutOfOrdernessTimestampExtractor but taking timestamp from previousElementTimestamp
 * See https://ci.apache.org/projects/flink/flink-docs-stable/dev/connectors/kafka.html#using-kafka-timestamps-and-flink-event-time-in-kafka-010
 */
class BounedOutOfOrderPreviousElementAssigner[T](maxOutOfOrdernessMillis: Long)
  extends AssignerWithPeriodicWatermarks[T] with Serializable {

  private var currentMaxTimestamp = Long.MinValue + maxOutOfOrdernessMillis

  private var lastEmittedWatermark = Long.MinValue

  override def extractTimestamp(element: T, previousElementTimestamp: Long): Long = {
    if (previousElementTimestamp > currentMaxTimestamp)
      currentMaxTimestamp = previousElementTimestamp
    previousElementTimestamp
  }

  override def getCurrentWatermark: Watermark = {
    val potentialWM = currentMaxTimestamp - maxOutOfOrdernessMillis
    if (potentialWM >= lastEmittedWatermark)
      lastEmittedWatermark = potentialWM
    new Watermark(lastEmittedWatermark)
  }

}
