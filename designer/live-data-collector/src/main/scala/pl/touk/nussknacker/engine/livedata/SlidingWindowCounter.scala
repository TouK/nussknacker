package pl.touk.nussknacker.engine.livedata

import java.time.{Clock, Instant}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

private[livedata] class SlidingWindowCounter[T](
    counterCreatedAt: Instant,
    windowSizeSeconds: Int
)(implicit clock: Clock) {

  private val buckets = new ConcurrentHashMap[Long, ConcurrentLinkedQueue[T]]()

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
    val windowStart      = Math.max(counterCreatedAt.getEpochSecond, currentEpochSecond - windowSizeSeconds)
    val windowsEnd       = currentEpochSecond
    val samplingInterval = windowsEnd - windowStart

    buckets.asScala.values
      .flatMap(_.asScala)
      .toList
      .groupBy(identity)
      .view
      .map { case (key, value) => (key, value.size) }
      .toMap
      .map { case (transition, count) =>
        transition -> BigDecimal(count)./(samplingInterval).setScale(4, BigDecimal.RoundingMode.HALF_UP)
      }
  }

  private val lastCleaned = new AtomicLong(0)

  private def cleanOldBuckets(now: Long): Unit = synchronized {
    // Clean old buckets at most once per second, not on each call
    if (lastCleaned.getAndSet(now) != now) {
      buckets
        .keySet()
        .asScala
        .filter(_ < cutoff(now))
        .foreach(buckets.remove)
    }
  }

  private def cutoff(now: Long): Long = now - windowSizeSeconds

  private def now(): Long = Instant.now(clock).getEpochSecond

}
