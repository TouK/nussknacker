package pl.touk.nussknacker.engine.livedata

import java.time.{Clock, Instant}
import java.util.concurrent.{ConcurrentLinkedQueue, ConcurrentSkipListMap}
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

private[livedata] class SlidingWindowCounter[T](
    counterCreatedAt: Instant,
    windowSizeSeconds: Int
)(implicit clock: Clock) {

  private val buckets = new ConcurrentSkipListMap[Long, ConcurrentLinkedQueue[T]]()

  def add(event: T): Unit = {
    val currentEpochSecond = now()
    cleanOldBuckets(currentEpochSecond)
    val bucket = buckets.computeIfAbsent(currentEpochSecond, _ => new ConcurrentLinkedQueue[T]())
    bucket.add(event)
  }

  def getThroughput: Map[T, BigDecimal] = {
    val currentEpochSecond = now()
    cleanOldBuckets(currentEpochSecond)

    // We want to calculate correct throughput just after the scenario is started
    val windowStart = Math.max(counterCreatedAt.getEpochSecond, currentEpochSecond - windowSizeSeconds)
    // We don't want to use the results from the second, that is currently in progress, because they may be incomplete
    // That is why the `windowsEnd` is the previous second, not the current one
    val windowEnd        = currentEpochSecond - 1
    val samplingInterval = windowEnd - windowStart + 1

    buckets.asScala.values
      .flatMap(_.asScala)
      .toList
      .groupBy(identity)
      .view
      .map { case (key, value) => (key, value.size) }
      .toMap
      .map { case (transition, count) =>
        transition -> {
          // We need to check whether samplingInterval > 0, because it equals -1 in the second when the scenario is deployed and 0 the second after that
          val throughput = if (samplingInterval <= 0) BigDecimal(0) else BigDecimal(count) / samplingInterval
          throughput.setScale(4, BigDecimal.RoundingMode.HALF_EVEN)
        }
      }
  }

  private val lastCleaned = new AtomicLong(0)

  private def cleanOldBuckets(now: Long): Unit = synchronized {
    // Clean old buckets at most once per second, not on each call
    // We clean buckets older than [currentEpochSecond - windowSizeSeconds] (non-inclusive)
    if (lastCleaned.getAndSet(now) != now) {
      buckets.headMap(now - windowSizeSeconds).clear()
    }
  }

  private def now(): Long = clock.instant().getEpochSecond

}
