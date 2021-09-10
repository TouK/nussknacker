package pl.touk.nussknacker.engine.kafka.generic

import org.scalatest.{FunSuite, Matchers}

import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.time.{DayOfWeek, LocalDateTime, ZoneId}

class DelayCalculatorSpec extends FunSuite with Matchers {

  test("should calculate fixed delay") {
    val fixedDelayProvider = new FixedDelayProvider(1000)
    fixedDelayProvider.calculateDelay(5000, 4000) shouldBe 0
    fixedDelayProvider.calculateDelay(5000, 4500) shouldBe 500
    fixedDelayProvider.calculateDelay(5000, 5000) shouldBe 1000
  }

  test("should calculate fixed delay based on schedule") {
    val fixedDelayProvider = new ScheduleDelayProvider(Schedule("WED", 10, 21))
    val wednesday = LocalDateTime.now().`with`(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
      .truncatedTo(ChronoUnit.HOURS).atZone(ZoneId.systemDefault())
    fixedDelayProvider.calculateDelay(wednesday.withHour(9).toInstant.toEpochMilli, 0) shouldBe 3600 * 1000
    fixedDelayProvider.calculateDelay(wednesday.withHour(10).toInstant.toEpochMilli, 0) shouldBe 0
    fixedDelayProvider.calculateDelay(wednesday.withHour(22).toInstant.toEpochMilli, 0) shouldBe (24 * 6 + 12) * 3600 * 1000
  }
}
