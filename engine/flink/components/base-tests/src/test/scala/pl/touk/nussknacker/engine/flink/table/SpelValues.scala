package pl.touk.nussknacker.engine.flink.table

object SpelValues {

  val spelBoolean = "true"
  val spelStr     = "'str'"

  val spelByte       = "123.byteValue"
  val spelShort      = "123.shortValue"
  val spelInt        = "123"
  val spelLong       = "123.longValue"
  val spelFloat      = "123.12.floatValue"
  val spelDouble     = "123.12.doubleValue"
  val spelBigDecimal = "T(java.math.BigDecimal).ONE"

  val spelLocalDate      = "T(java.time.LocalDate).parse('2020-12-31')"
  val spelLocalTime      = "T(java.time.LocalTime).parse('10:15')"
  val spelLocalDateTime  = "T(java.time.LocalDateTime).parse('2020-12-31T10:15')"
  val spelOffsetDateTime = "T(java.time.OffsetDateTime).parse('2020-12-31T10:15+01:00')"
  val spelInstant        = "T(java.time.Instant).parse('2020-12-31T10:15:00Z')"

  val numberPrimitiveLiteralExpressions: List[String] = List(
    spelByte,
    spelShort,
    spelInt,
    spelLong,
    spelFloat,
    spelDouble
  )

  val tableApiSupportedTimeLiteralExpressions: List[String] = List(
    spelLocalDate,
    spelLocalTime,
    spelLocalDateTime,
    spelInstant,
  )

}
