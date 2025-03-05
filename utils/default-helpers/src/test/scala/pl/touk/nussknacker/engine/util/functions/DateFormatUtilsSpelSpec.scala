package pl.touk.nussknacker.engine.util.functions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime, ZoneId, ZoneOffset}
import scala.reflect.runtime.universe._

class DateFormatUtilsSpelSpec extends AnyFunSuite with BaseSpelSpec with Matchers {

  test("parsing") {
    evaluate[LocalDate]("#DATE_FORMAT.parseLocalDate('2020-01-01')", Map.empty) shouldEqual LocalDate.of(2020, 1, 1)
    evaluate[LocalTime]("#DATE_FORMAT.parseLocalTime('11:12:13')", Map.empty) shouldEqual LocalTime.of(11, 12, 13)
    evaluate[LocalDateTime]("#DATE_FORMAT.parseLocalDateTime('2020-01-01T11:12:13')", Map.empty) shouldEqual
      LocalDateTime.of(2020, 1, 1, 11, 12, 13)
    evaluate[Instant]("#DATE_FORMAT.parseInstant('2020-01-01T11:12:13Z')", Map.empty) shouldEqual
      LocalDateTime.of(2020, 1, 1, 11, 12, 13).atZone(ZoneOffset.UTC).toInstant
    evaluate[OffsetDateTime]("#DATE_FORMAT.parseOffsetDateTime('2020-01-01T11:12:13+01:00')", Map.empty) shouldEqual
      LocalDateTime.of(2020, 1, 1, 11, 12, 13).atOffset(ZoneOffset.of("+01:00"))
    evaluate[ZonedDateTime](
      "#DATE_FORMAT.parseZonedDateTime('2020-01-01T11:12:13+01:00[Europe/Warsaw]')",
      Map.empty
    ) shouldEqual
      LocalDateTime.of(2020, 1, 1, 11, 12, 13).atZone(ZoneId.of("Europe/Warsaw"))
  }

  test("formatting") {
    val fixedZoned: ZonedDateTime = ZonedDateTime.of(2021, 1, 1, 1, 0, 0, 0, ZoneId.of("Europe/Warsaw"))
    val variables = Map[String, Any](
      "instant"       -> fixedZoned.toInstant,
      "offset"        -> fixedZoned.toOffsetDateTime,
      "zoned"         -> fixedZoned,
      "localDateTime" -> fixedZoned.toLocalDateTime,
      "localDate"     -> fixedZoned.toLocalDate,
      "localTime"     -> fixedZoned.toLocalTime,
    )
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[String]("#DATE_FORMAT.format(#instant)") shouldEqual "2021-01-01T00:00:00Z"
    evaluateExpr[String]("#DATE_FORMAT.format(#offset)") shouldEqual "2021-01-01T01:00:00+01:00"
    evaluateExpr[String]("#DATE_FORMAT.format(#zoned)") shouldEqual "2021-01-01T01:00:00+01:00[Europe/Warsaw]"
    evaluateExpr[String]("#DATE_FORMAT.format(#localDateTime)") shouldEqual "2021-01-01T01:00:00"
    evaluateExpr[String]("#DATE_FORMAT.format(#localDate)") shouldEqual "2021-01-01"
    evaluateExpr[String]("#DATE_FORMAT.format(#localTime)") shouldEqual "01:00:00"
  }

  test("custom formatters") {
    val fixedZoned: ZonedDateTime = ZonedDateTime.of(2021, 10, 12, 1, 0, 0, 0, ZoneId.of("Europe/Warsaw"))
    val variables                 = Map[String, Any]("zoned" -> fixedZoned)
    def evaluateExpr[T: TypeTag](expr: String): T = evaluate(expr, variables)

    evaluateExpr[String]("#DATE_FORMAT.formatter('EEEE').format(#zoned)") shouldEqual "Tuesday"
    evaluateExpr[String]("#DATE_FORMAT.formatter('EEEE', 'PL').format(#zoned)") shouldEqual "wtorek"
    evaluateExpr[LocalDate](
      "#DATE_FORMAT.parseLocalDate('2021-01-01', #DATE_FORMAT.formatter('yyyy-MM-dd'))"
    ) shouldEqual LocalDate.of(2021, 1, 1)
    evaluateExpr[LocalDate](
      "#DATE_FORMAT.parseLocalDate('2021-1-1', #DATE_FORMAT.lenientFormatter('yyyy-MM-dd'))"
    ) shouldEqual LocalDate.of(2021, 1, 1)
  }

}
