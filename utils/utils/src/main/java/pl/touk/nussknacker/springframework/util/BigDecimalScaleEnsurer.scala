package pl.touk.nussknacker.springframework.util

import java.math.RoundingMode;
// TODO_PAWEL jaki package?

object BigDecimalScaleEnsurer {
  // visible for testing
  val DEFAULT_BIG_DECIMAL_SCALE = 18

  def ensureBigDecimalScale(value: java.math.BigDecimal): java.math.BigDecimal = {
    value.setScale(Math.max(value.scale(), BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE), RoundingMode.UNNECESSARY)
  }

}
