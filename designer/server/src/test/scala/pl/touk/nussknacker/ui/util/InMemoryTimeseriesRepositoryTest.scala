package pl.touk.nussknacker.ui.util

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import java.time.{Clock, Duration, Instant, ZoneId}

class InMemoryTimeseriesRepositoryTest extends AnyFunSuiteLike with Matchers {

  test("save an entry and fetch it") {
    val now  = Instant.ofEpochMilli(0)
    val repo = InMemoryTimeseriesRepository[String](Duration.ofHours(1), clockForInstant(() => now))
    repo.saveEntry("foo")
    repo.fetchEntries(Instant.ofEpochMilli(0)) shouldBe List("foo")
    repo.fetchEntries(Instant.ofEpochMilli(1)) shouldBe List.empty
  }

  test("save multiple entries") {
    var now  = Instant.ofEpochMilli(0)
    val repo = InMemoryTimeseriesRepository[String](Duration.ofHours(1), clockForInstant(() => now))
    repo.saveEntry("foo")
    now = Instant.ofEpochMilli(1)
    repo.saveEntry("bar")
    repo.fetchEntries(Instant.ofEpochMilli(0)) shouldBe List("foo", "bar")
    repo.fetchEntries(Instant.ofEpochMilli(1)) shouldBe List("bar")
    repo.fetchEntries(Instant.ofEpochMilli(2)) shouldBe List.empty
  }

  test("evict entries with configured delay") {
    var now           = Instant.ofEpochMilli(0)
    val evictionDelay = Duration.ofSeconds(10)
    val repo          = InMemoryTimeseriesRepository[String](evictionDelay, clockForInstant(() => now))
    repo.saveEntry("foo")
    repo.fetchEntries(Instant.ofEpochMilli(0)) shouldBe List("foo")

    now = Instant.ofEpochMilli(evictionDelay.toMillis - 1)
    repo.evictOldEntries()
    repo.fetchEntries(Instant.ofEpochMilli(0)) shouldBe List("foo")

    now = Instant.ofEpochMilli(evictionDelay.toMillis)
    repo.evictOldEntries()
    repo.fetchEntries(Instant.ofEpochMilli(0)) shouldBe List.empty
  }

  private def clockForInstant(currentInstant: () => Instant): Clock = {
    new Clock {
      override def getZone: ZoneId = ZoneId.systemDefault()

      override def withZone(zone: ZoneId): Clock = ???

      override def instant(): Instant = currentInstant()
    }
  }

}
