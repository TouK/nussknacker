package pl.touk.nussknacker.engine.api.json

import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class FromJsonDecoderTest extends AnyFunSuiteLike with Matchers with OptionValues {

  test("json number decoding pick the narrowest type") {
    FromJsonDecoder.jsonToAny(Json.fromInt(1)) shouldBe 1
    FromJsonDecoder.jsonToAny(Json.fromInt(Integer.MAX_VALUE)) shouldBe Integer.MAX_VALUE
    FromJsonDecoder.jsonToAny(Json.fromLong(Long.MaxValue)) shouldBe Long.MaxValue
    FromJsonDecoder.jsonToAny(
      Json.fromBigDecimal(java.math.BigDecimal.valueOf(Double.MaxValue))
    ) shouldBe java.math.BigDecimal.valueOf(Double.MaxValue)
    val moreThanLongMaxValue = BigDecimal(Long.MaxValue) * 10
    FromJsonDecoder.jsonToAny(Json.fromBigDecimal(moreThanLongMaxValue)) shouldBe moreThanLongMaxValue.bigDecimal
  }

}
