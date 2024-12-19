package pl.touk.nussknacker.springframework.util

import java.math.RoundingMode;

/*
In spel division of two big decimals uses divide operator org.springframework.expression.spel.ast.OpDivide.
In this operator if one of the divided numbers is BigDecimal then second number is also converted to BigDecimal using org.springframework.util.NumberUtils class
and resulting BigDecimal will have scale (that is number of stored digits to the right of decimal point) equal to max of the scales of two divided numbers.
The behaviour of org.springframework.util.NumberUtils is to convert integers to BigDecimals in such a way, that they have scale equal to 0.
This may lead to unexpected behaviour. For instance
"(1).toBigDecimal / 2"
would evaluate to BigDecimal("0") (we use org.springframework.util.NumberUtils to convert 1 to BigDecimal).
This is not an acceptable result because
"(1).toDouble / 2"
will always evaluate to "0.5" and user would expect higher computation precision from BigDecimal.

To avoid such issues we use this class to make sure that BigDecimals which are created always have scale of at least DEFAULT_BIG_DECIMAL_SCALE.
BigDecimals can be created in org.springframework.util.NumberUtils conversion, or in our .toBigDecimal spel extension.

There is the risk that big decimals enter process in other ways (for instance from jdbc controllers or from scenario input),
and they may have small scales. This may again lead to unexpected
behaviour when using division operator. This issue can be solved by using our own version of OpDivide class, but for now we decided
not to do it.
*/
object BigDecimalScaleEnsurer {
  // visible for testing
  val DEFAULT_BIG_DECIMAL_SCALE = 18

  def ensureBigDecimalScale(value: java.math.BigDecimal): java.math.BigDecimal = {
    value.setScale(Math.max(value.scale(), BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE), RoundingMode.UNNECESSARY)
  }
}
