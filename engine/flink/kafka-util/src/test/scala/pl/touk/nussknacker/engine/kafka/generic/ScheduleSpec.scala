package pl.touk.nussknacker.engine.kafka.generic

import org.scalatest.{FunSuite, Matchers}

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDateTime}

class ScheduleSpec extends FunSuite with Matchers {

  test("should return next scheduled date on different day") {

    val schedule = Schedule("MON,FRI,SAT", 9, 12)
    schedule.nextAfter(date(DayOfWeek.WEDNESDAY, 8, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.WEDNESDAY, 9, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.WEDNESDAY, 12, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.WEDNESDAY, 13, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.THURSDAY, 0, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.THURSDAY, 11, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.THURSDAY, 22, 0)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.FRIDAY, 12, 0)) shouldBe date(DayOfWeek.SATURDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.FRIDAY, 13, 0)) shouldBe date(DayOfWeek.SATURDAY, 9, 0)
  }

  test("should return next scheduled date on the same day") {

    val schedule = Schedule("MON,FRI,SAT", 9, 12)
    schedule.nextAfter(date(DayOfWeek.MONDAY, 0, 0)) shouldBe date(DayOfWeek.MONDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.MONDAY, 9, 0)) shouldBe date(DayOfWeek.MONDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.MONDAY, 11, 59)) shouldBe date(DayOfWeek.MONDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.FRIDAY, 8, 59)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.FRIDAY, 9, 1)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
    schedule.nextAfter(date(DayOfWeek.FRIDAY, 11, 59)) shouldBe date(DayOfWeek.FRIDAY, 9, 0)
  }

  test("should return next scheduled date on next week") {

    val schedule = Schedule("MON", 9, 12)
    schedule.nextAfter(date(DayOfWeek.MONDAY, 12, 0)) shouldBe date(DayOfWeek.MONDAY, 9, 0).plusWeeks(1)
    schedule.nextAfter(date(DayOfWeek.MONDAY, 14, 0)) shouldBe date(DayOfWeek.MONDAY, 9, 0).plusWeeks(1)
    schedule.nextAfter(date(DayOfWeek.SUNDAY, 8, 0)) shouldBe date(DayOfWeek.MONDAY, 9, 0).plusWeeks(1)
  }
  def date(dayOfWeek: DayOfWeek, hour: Int, minutes: Int): LocalDateTime = {
    LocalDateTime.of(2021, 3, 1, hour, minutes).`with`(TemporalAdjusters.nextOrSame(dayOfWeek))
  }
}
