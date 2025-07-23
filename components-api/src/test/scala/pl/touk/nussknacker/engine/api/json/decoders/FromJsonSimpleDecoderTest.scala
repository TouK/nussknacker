package pl.touk.nussknacker.engine.api.json.decoders

import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class FromJsonSimpleDecoderTest extends AnyFunSuiteLike with Matchers with OptionValues {

  test("json number decoding pick the narrowest type") {
    FromJsonSimpleDecoder.jsonToAny(Json.fromInt(1)) shouldBe 1
    FromJsonSimpleDecoder.jsonToAny(Json.fromInt(Integer.MAX_VALUE)) shouldBe Integer.MAX_VALUE
    FromJsonSimpleDecoder.jsonToAny(Json.fromLong(Long.MaxValue)) shouldBe Long.MaxValue
    FromJsonSimpleDecoder.jsonToAny(
      Json.fromBigDecimal(java.math.BigDecimal.valueOf(Double.MaxValue))
    ) shouldBe java.math.BigDecimal.valueOf(Double.MaxValue)
    val moreThanLongMaxValue = BigDecimal(Long.MaxValue) * 10
    FromJsonSimpleDecoder.jsonToAny(Json.fromBigDecimal(moreThanLongMaxValue)) shouldBe moreThanLongMaxValue.bigDecimal
  }

}
