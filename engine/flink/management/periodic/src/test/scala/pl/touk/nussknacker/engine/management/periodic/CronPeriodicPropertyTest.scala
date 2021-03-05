package pl.touk.nussknacker.engine.management.periodic

import java.time.{LocalDateTime, ZoneId}

import org.scalatest.{FunSuite, Matchers}

class CronPeriodicPropertyTest extends FunSuite with Matchers {

  private val clock_2020_07_28_12_20 = new Clock {
    override def currentTimestamp: Long = LocalDateTime.of(2020, 7, 28, 12, 20, 10)
      .atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

  /*
  test("should return closest to now nextRunAt date") {
    CronPeriodicProperty(
      "0 0 * * * ?|0 43 0,12,16 28 MAY,JUL ? 2020|0 50 0,12 28 JUL ? 2020|0 50 0,12 28 JUL ? 2019"
    ).nextRunAt(clock_2020_07_28_12_20) shouldBe Right(Some(LocalDateTime.of(2020, 7, 28, 12, 43, 0)))
  }

  test("should return none when cron time passed") {
    CronPeriodicProperty("0 50 0,12 28 JUL ? 2019")
      .nextRunAt(clock_2020_07_28_12_20) shouldBe Right(None)
  }

  test("should fail for invalid expression") {
    CronPeriodicProperty("invalid")
      .nextRunAt(SystemClock).isLeft shouldBe true
  } */
}
