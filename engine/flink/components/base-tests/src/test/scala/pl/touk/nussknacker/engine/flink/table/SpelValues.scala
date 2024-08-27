package pl.touk.nussknacker.engine.flink.table

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

object SpelValues {

  val spelBoolean = "true".spel
  val spelStr     = "'str'".spel

  val spelByte       = "123.byteValue".spel
  val spelShort      = "123.shortValue".spel
  val spelInt        = "123".spel
  val spelLong       = "123.longValue".spel
  val spelFloat      = "123.12.floatValue".spel
  val spelDouble     = "123.12.doubleValue".spel
  val spelBigDecimal = "T(java.math.BigDecimal).ONE".spel

  val spelLocalDate      = "T(java.time.LocalDate).parse('2020-12-31')".spel
  val spelLocalTime      = "T(java.time.LocalTime).parse('10:15')".spel
  val spelLocalDateTime  = "T(java.time.LocalDateTime).parse('2020-12-31T10:15')".spel
  val spelOffsetDateTime = "T(java.time.OffsetDateTime).parse('2020-12-31T10:15+01:00')".spel
  val spelInstant        = "T(java.time.Instant).parse('2020-12-31T10:15:00Z')".spel

  val numberPrimitiveLiteralExpressions: List[Expression] = List(
    spelByte,
    spelShort,
    spelInt,
    spelLong,
    spelFloat,
    spelDouble
  )

  val tableApiSupportedTimeLiteralExpressions: List[Expression] = List(
    spelLocalDate,
    spelLocalTime,
    spelLocalDateTime,
    spelInstant,
  )

}
