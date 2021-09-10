package pl.touk.nussknacker.engine.kafka.generic

import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.time.{DayOfWeek, LocalDateTime, ZoneId}

object Schedule {

  val textToDayOfWeek: Map[String, DayOfWeek] = Map(
    "MON" -> DayOfWeek.MONDAY,
    "TUE" -> DayOfWeek.TUESDAY,
    "WED" -> DayOfWeek.WEDNESDAY,
    "THU" -> DayOfWeek.THURSDAY,
    "FRI" -> DayOfWeek.FRIDAY,
    "SAT" -> DayOfWeek.SATURDAY,
    "SUN" -> DayOfWeek.SUNDAY,
  )

  def apply(days: String, hourFrom: Int, hourTo: Int): Schedule = {
    new Schedule(days.toUpperCase().split(", *").map(day => textToDayOfWeek(day)).distinct.toList, hourFrom, hourTo)
  }
}

class Schedule(val days: List[DayOfWeek],
               val hourFrom: Int,
               val hourTo: Int) {
  def nextAfter(date: LocalDateTime): LocalDateTime = {
    (days.map(day => date.`with`(TemporalAdjusters.next(day))) union days.map(day => date.`with`(TemporalAdjusters.nextOrSame(day))))
      .map(d => d.truncatedTo(ChronoUnit.HOURS).withHour(hourTo))
      .filter(d => d.isAfter(date))
      .min[LocalDateTime](Ordering.by(date => date.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli)).withHour(hourFrom)
  }

}
