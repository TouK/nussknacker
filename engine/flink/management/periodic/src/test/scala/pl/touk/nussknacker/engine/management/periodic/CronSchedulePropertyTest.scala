package pl.touk.nussknacker.engine.management.periodic

import java.time.{Clock, LocalDateTime, ZoneId, ZonedDateTime}
import org.scalatest.{FunSuite, Matchers}

class CronSchedulePropertyTest extends FunSuite with Matchers {

  private val clock_2020_07_28_12_20 = Clock.fixed(
    ZonedDateTime.of(2020, 7, 28, 12, 20, 10, 0, ZoneId.systemDefault()).toInstant, ZoneId.systemDefault())


  test("should return closest to now nextRunAt date") {
    CronScheduleProperty(
      "0 0 * * * ?|0 43 0,12,16 28 MAY,JUL ? 2020|0 50 0,12 28 JUL ? 2020|0 50 0,12 28 JUL ? 2019"
    ).nextRunAt(clock_2020_07_28_12_20) shouldBe Right(Some(LocalDateTime.of(2020, 7, 28, 12, 43, 0)))
  }

  test("should return none when cron time passed") {
    CronScheduleProperty("0 50 0,12 28 JUL ? 2019")
      .nextRunAt(clock_2020_07_28_12_20) shouldBe Right(None)
  }

  test("should fail for invalid expression") {
    CronScheduleProperty("invalid")
      .nextRunAt(Clock.systemDefaultZone()).isLeft shouldBe true
  }
}
