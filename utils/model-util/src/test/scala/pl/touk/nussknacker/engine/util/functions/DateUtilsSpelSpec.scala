package pl.touk.nussknacker.engine.util.functions

import org.scalatest.{FunSuite, Matchers}

import java.time._
import scala.reflect.runtime.universe._

class DateUtilsSpelSpec extends FunSuite with BaseSpelSpec with Matchers {

  test("now returning various time classes") {
    evaluate[Instant]("#DATE.now", Map.empty) shouldEqual fixedZoned.toInstant
    evaluate[ZonedDateTime]("#DATE.nowAtZone(#DATE.zuluTimeZone)", Map.empty) shouldEqual fixedZoned
    evaluate[ZonedDateTime]("#DATE.nowAtZone('Z')", Map.empty) shouldEqual fixedZoned
    evaluate[OffsetDateTime]("#DATE.nowAtOffset(#DATE.UTCOffset)", Map.empty) shouldEqual fixedZoned.toOffsetDateTime
    evaluate[OffsetDateTime]("#DATE.nowAtOffset('+00:00')", Map.empty) shouldEqual fixedZoned.toOffsetDateTime
  }

  test("conversions between various time classes") {
    val fixedZoned: ZonedDateTime = ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    val variables = Map[String, Any](
      "epoch" -> fixedZoned.toInstant.toEpochMilli,
      "instant" -> fixedZoned.toInstant,
      "offset" -> fixedZoned.toOffsetDateTime,
      "zoned" -> fixedZoned,
      "localDateTime" -> fixedZoned.toLocalDateTime,
      "localDate" -> fixedZoned.toLocalDate,
      "localTime" -> fixedZoned.toLocalTime,
    )
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Long]("#instant.toEpochMilli") shouldEqual fixedZoned.toInstant.toEpochMilli
    evaluateExpr[Long]("#DATE.toEpochMilli(#offset)") shouldEqual fixedZoned.toInstant.toEpochMilli
    evaluateExpr[Long]("#DATE.toEpochMilli(#zoned)") shouldEqual fixedZoned.toInstant.toEpochMilli
    evaluateExpr[Long]("#DATE.toEpochMilli(#localDateTime, #DATE.zuluTimeZone)") shouldEqual fixedZoned.toInstant.toEpochMilli
    evaluateExpr[Long]("#DATE.toEpochMilli(#localDateTime, #DATE.UTCOffset)") shouldEqual fixedZoned.toInstant.toEpochMilli

    evaluateExpr[Instant]("#DATE.toInstant(#epoch)") shouldEqual fixedZoned.toInstant
    evaluateExpr[Instant]("#offset.toInstant") shouldEqual fixedZoned.toInstant
    evaluateExpr[Instant]("#zoned.toInstant") shouldEqual fixedZoned.toInstant
    evaluateExpr[Instant]("#localDateTime.atZone(#DATE.zuluTimeZone).toInstant") shouldEqual fixedZoned.toInstant

    evaluateExpr[OffsetDateTime]("#DATE.toInstant(#epoch).atOffset(#DATE.UTCOffset)") shouldEqual fixedZoned.toOffsetDateTime
    evaluateExpr[OffsetDateTime]("#instant.atOffset(#DATE.UTCOffset)") shouldEqual fixedZoned.toOffsetDateTime
    evaluateExpr[OffsetDateTime]("#zoned.toOffsetDateTime") shouldEqual fixedZoned.toOffsetDateTime
    evaluateExpr[OffsetDateTime]("#localDateTime.atOffset(#DATE.UTCOffset)") shouldEqual fixedZoned.toOffsetDateTime

    evaluateExpr[ZonedDateTime]("#DATE.toInstant(#epoch).atZone(#DATE.zuluTimeZone)") shouldEqual fixedZoned
    evaluateExpr[ZonedDateTime]("#instant.atZone(#DATE.zuluTimeZone)") shouldEqual fixedZoned
    evaluateExpr[ZonedDateTime]("#offset.atZoneSameInstant(#DATE.zuluTimeZone)") shouldEqual fixedZoned
    evaluateExpr[ZonedDateTime]("#localDateTime.atZone(#DATE.zuluTimeZone)") shouldEqual fixedZoned

    evaluateExpr[LocalDateTime]("#DATE.toInstant(#epoch).atZone(#DATE.zuluTimeZone).toLocalDateTime") shouldEqual fixedZoned.toLocalDateTime
    evaluateExpr[LocalDateTime]("#instant.atZone(#DATE.zuluTimeZone).toLocalDateTime") shouldEqual fixedZoned.toLocalDateTime
    evaluateExpr[LocalDateTime]("#offset.toLocalDateTime") shouldEqual fixedZoned.toLocalDateTime
    evaluateExpr[LocalDateTime]("#zoned.toLocalDateTime") shouldEqual fixedZoned.toLocalDateTime
    evaluateExpr[LocalDateTime]("#DATE.localDateTime(#localDate, #localTime)") shouldEqual fixedZoned.toLocalDateTime
  }

  test("some shortcut conversions using default time zone") {
    val variables = Map[String, Any](
      "instant" -> Instant.ofEpochMilli(0L),
      "zoned" -> ZonedDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneId.of("Europe/Moscow")),
      "localDateTime" -> LocalDateTime.of(2023, 2, 3, 4, 5, 6, 7)
    )
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[ZonedDateTime]("#DATE.nowAtDefaultTimeZone") shouldEqual evaluateExpr[ZonedDateTime]("#DATE.nowAtZone(#DATE.zuluTimeZone)")

    evaluateExpr[Instant]("#DATE.toInstantAtDefaultTimeZone(#localDateTime)") shouldEqual evaluateExpr[Instant]("#localDateTime.atZone(#DATE.zuluTimeZone).toInstant")

    evaluateExpr[ZonedDateTime]("#instant.atZone(#DATE.defaultTimeZone)") shouldEqual evaluateExpr[ZonedDateTime]("#instant.atZone(#DATE.zuluTimeZone)")
    evaluateExpr[ZonedDateTime]("#localDateTime.atZone(#DATE.defaultTimeZone)") shouldEqual evaluateExpr[ZonedDateTime]("#localDateTime.atZone(#DATE.zuluTimeZone)")
  }

  test("operation on week days") {
    val someMonday = LocalDateTime.of(2021, 10, 11, 0, 0).atZone(ZoneOffset.UTC)
    val variables = Map[String, Any](
      "zoned" -> someMonday)
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Boolean]("#zoned.getDayOfWeek == #DATE.MONDAY") shouldEqual true
    evaluateExpr[Boolean]("#zoned.getDayOfWeek == #DATE.FRIDAY") shouldEqual false
  }

  test("period and duration computations between two dates") {
    val someDay = LocalDateTime.of(2021, 10, 11, 0, 0).atZone(ZoneOffset.UTC)
    val variables = Map[String, Any](
      "zoned" -> someDay)
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Int]("#DATE.periodBetween(#zoned, #zoned.plusYears(1)).getYears") shouldEqual 1
    evaluateExpr[Int]("#DATE.periodBetween(#zoned, #zoned.plusMonths(1)).getMonths") shouldEqual 1
    evaluateExpr[Int]("#DATE.periodBetween(#zoned, #zoned.plusDays(1)).getDays") shouldEqual 1

    evaluateExpr[Long]("#DATE.durationBetween(#zoned, #zoned.plusYears(1)).toDays") shouldEqual 365
  }

  test("check if time is in given range") {
    val variables = Map[String, Any](
      "at8" ->  LocalTime.of(8, 0),
      "at9" ->  LocalTime.of(9, 0),
      "at10" ->  LocalTime.of(10, 0),
      "at16" ->  LocalTime.of(16, 0),
      "at17" ->  LocalTime.of(17, 0),
      "at18" ->  LocalTime.of(18, 0))
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Boolean]("#DATE.isBetween(#at9, '09:00', '17:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#at17, '09:00', '17:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#at8, '09:00', '17:00')") shouldEqual false
    evaluateExpr[Boolean]("#DATE.isBetween(#at18, '09:00', '17:00')") shouldEqual false

    evaluateExpr[Boolean]("#DATE.isBetween(#at17, '17:00', '09:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#at9, '17:00', '09:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#at16, '17:00', '09:00')") shouldEqual false
    evaluateExpr[Boolean]("#DATE.isBetween(#at10, '17:00', '09:00')") shouldEqual false

    evaluateExpr[Boolean]("#at10.isAfter('09:00') AND #at10.isBefore('11:00')") shouldEqual true
  }

  test("check if date is in given range") {
    val variables = Map[String, Any](
      "date" ->  LocalDate.of(2020, 6, 1))
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-06-01', '2020-07-01')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-05-01', '2020-06-01')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-07-01', '2020-08-01')") shouldEqual false

    evaluateExpr[Boolean]("#date.isAfter('2020-05-01') AND #date.isBefore('2020-07-01')") shouldEqual true
  }

  test("check if date time is in given range") {
    val variables = Map[String, Any](
      "date" ->  LocalDateTime.of(2020, 6, 1, 11, 0))
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-06-01T11:00:00', '2020-07-01T11:00:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-05-01T11:00:00', '2020-06-01T11:00:00')") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#date, '2020-06-01T11:00:01', '2020-07-01T11:00:00')") shouldEqual false

    evaluateExpr[Boolean]("#date.isAfter('2020-05-01T11:00:00') AND #date.isBefore('2020-07-01T11:00:00')") shouldEqual true
  }

  test("check if day of week is in given range") {
    val variables = Map[String, Any](
      "monday" ->  DayOfWeek.MONDAY,
      "friday" ->  DayOfWeek.FRIDAY)
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[Boolean]("#DATE.isBetween(#monday, #DATE.MONDAY, #DATE.FRIDAY)") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#friday, #DATE.MONDAY, #DATE.FRIDAY)") shouldEqual true
    evaluateExpr[Boolean]("#DATE.isBetween(#monday, #DATE.SATURDAY, #DATE.SUNDAY)") shouldEqual false
    evaluateExpr[Boolean]("#DATE.isBetween(#monday, #DATE.SUNDAY, #DATE.MONDAY)") shouldEqual true
  }

}
