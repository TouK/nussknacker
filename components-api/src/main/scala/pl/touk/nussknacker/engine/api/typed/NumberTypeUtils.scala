package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object NumberTypeUtils {

  def zeroForType(typ: TypingResult): Number = {
    // we check equality of types to avoid conversions
    if (typ == Typed[java.lang.Byte]) java.lang.Byte.valueOf(0.byteValue())
    else if (typ == Typed[java.lang.Short]) java.lang.Short.valueOf(0.shortValue())
    else if (typ == Typed[java.lang.Integer]) java.lang.Integer.valueOf(0)
    else if (typ == Typed[java.lang.Long]) java.lang.Long.valueOf(0)
    else if (typ == Typed[java.math.BigInteger]) java.math.BigInteger.ZERO
    else if (typ == Typed[java.lang.Float]) java.lang.Float.valueOf(0)
    else if (typ == Typed[java.lang.Double]) java.lang.Double.valueOf(0)
    else if (typ == Typed[java.math.BigDecimal]) java.math.BigDecimal.ZERO
    // in case of some unions
    else if (typ.canBeConvertedTo(Typed[java.lang.Integer])) java.lang.Integer.valueOf(0)
    // double is quite safe - it can be converted to any Number
    else if (typ.canBeConvertedTo(Typed[Number])) java.lang.Double.valueOf(0)
    else throw new IllegalArgumentException(s"Not expected type: ${typ.display}, should be Number")
  }

}
