package pl.touk.nussknacker.engine.util

import scala.util.Try

object convert {

  abstract class StringToNumberConverter[T](tryToConvert: String => T) {
    def unapply(str: String): Option[T] =
      Try(tryToConvert(str)).map(Some(_)).recover {
        case _: NumberFormatException => None
      }.get
  }

  object IntValue extends StringToNumberConverter[Int](_.toInt)

  object LongValue extends StringToNumberConverter[Long](_.toLong)

  object DoubleValue extends StringToNumberConverter[Double](_.toDouble)

  object FloatValue extends StringToNumberConverter[Double](_.toFloat)

  object BooleanValue extends StringToNumberConverter[Boolean](_.toBoolean)

  object BigDecimalValue extends StringToNumberConverter[BigDecimal](BigDecimal(_))

}
