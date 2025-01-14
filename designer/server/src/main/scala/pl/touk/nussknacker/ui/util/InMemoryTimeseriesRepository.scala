package pl.touk.nussknacker.ui.util

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.ConcurrentSkipListMap
import scala.jdk.CollectionConverters._

class InMemoryTimeseriesRepository[EntryT](
    timeline: ConcurrentSkipListMap[Instant, EntryT],
    evictionDelay: Duration,
    clock: Clock
) {

  def saveEntry(entry: EntryT): Unit = {
    timeline.put(clock.instant(), entry)
  }

  def fetchEntries(lowerLimit: Instant): List[EntryT] = {
    timeline.tailMap(lowerLimit).values().asScala.toList
  }

  def evictOldEntries(): Unit = {
    timeline.headMap(clock.instant().minus(evictionDelay), true).clear()
  }

}

object InMemoryTimeseriesRepository {

  def apply[EntryT](retentionDelay: Duration, clock: Clock): InMemoryTimeseriesRepository[EntryT] = {
    new InMemoryTimeseriesRepository[EntryT](
      new ConcurrentSkipListMap[Instant, EntryT],
      retentionDelay,
      clock
    )
  }

}
