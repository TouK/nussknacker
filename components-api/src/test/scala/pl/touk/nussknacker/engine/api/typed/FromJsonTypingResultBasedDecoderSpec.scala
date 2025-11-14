package pl.touk.nussknacker.engine.api.typed

import com.typesafe.scalalogging.LazyLogging
import io.circe.{DecodingFailure, Json}
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.fromInstance
import pl.touk.nussknacker.engine.util.json.{SampleEnum, SampleEnumWithToStringOverridden}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.jdk.CollectionConverters._

class FromJsonTypingResultBasedDecoderSpec
    extends AnyFunSuite
    with EitherValuesDetailedMessage
    with Matchers
    with LazyLogging {

  test("decodeValue should decode Record fields correctly when all fields are present") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name" -> "Alice".asJson,
      "age"  -> 30.asJson
    )

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "age"  -> 30
      ).asJava
    )
  }

  test("decodeValue should ignore missing Record field") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name" -> "Alice".asJson
    )

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor).rightValue shouldBe Map(
      "name" -> "Alice"
    ).asJava
  }

  test("decodeValue should decode extra fields using generic json decoding strategy") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name"       -> "Alice".asJson,
      "age"        -> 30.asJson,
      "occupation" -> "nurse".asJson,
    )

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name"       -> "Alice",
        "age"        -> 30,
        "occupation" -> "nurse"
      ).asJava
    )
  }

  test("decodeValue should handle nested records correctly") {
    val json = Json.obj(
      "name" -> "Alice".asJson,
      "address" -> Json.obj(
        "street"      -> "Main St".asJson,
        "houseNumber" -> "123".asJson
      )
    )

    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "address" -> Typed.record(
          Map(
            "street"      -> Typed.fromInstance("Main St"),
            "houseNumber" -> Typed.fromInstance("123")
          )
        )
      )
    )

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "address" -> Map(
          "street"      -> "Main St",
          "houseNumber" -> "123"
        ).asJava
      ).asJava
    )
  }

  test("decodeValue should handle null json despite of typing") {
    val json = Json.Null

    val typedRecord = Typed.typedClass(classOf[String])

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(null)
  }

  test("decodeValue should return error when boolean is used in context where numeric value was expected") {
    val json = Json.fromBoolean(false)

    val typedRecord = Typed[Int]

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldBe Symbol("left")
  }

  test("should decode enum class") {
    val givenValue  = SampleEnum.DOLOR
    val encodedJson = ToJsonEncoder.default.encodeUnsafe(givenValue)
    encodedJson shouldEqual Json.fromString("DOLOR")
    val decodedValue = FromJsonTypingResultBasedDecoder.decodeValue(Typed[SampleEnum], encodedJson.hcursor).rightValue
    decodedValue shouldBe givenValue
  }

  test("should decode union of possible values") {
    val typ                   = Typed(Typed.fromInstance(1), Typed.fromInstance(2), Typed.fromInstance(3))
    val resultForCorrectValue = FromJsonTypingResultBasedDecoder.decodeValue(typ, Json.fromInt(1).hcursor)
    resultForCorrectValue.rightValue shouldBe 1

    val resultForInvalidValue = FromJsonTypingResultBasedDecoder.decodeValue(typ, Json.fromInt(4).hcursor)
    resultForInvalidValue should matchPattern {
      case Left(DecodingFailure("Expected one of [Integer(1), Integer(2), Integer(3)]", _)) =>
    }
  }

  // This test demonstrates how kafka TimestampType is handled
  test("should decode enum class with toString overridden") {
    val givenValue  = SampleEnumWithToStringOverridden.DOLOR
    val encodedJson = ToJsonEncoder.default.encodeUnsafe(givenValue)
    encodedJson shouldEqual Json.fromString("DOLOR")
    val decodedValue = FromJsonTypingResultBasedDecoder
      .decodeValue(Typed[SampleEnumWithToStringOverridden], encodedJson.hcursor)
      .rightValue
    decodedValue shouldBe givenValue
  }

  test("should return error when enum has invalid value") {
    FromJsonTypingResultBasedDecoder
      .decodeValue(Typed[SampleEnum], Json.fromString("illegal-value").hcursor)
      .leftValue
      .message shouldBe "No enum constant pl.touk.nussknacker.engine.util.json.SampleEnum.illegal-value"
  }

  test("should decode Map") {
    forAll(
      Table(
        ("givenMap", "givenType"),
        (Map("foo" -> 123).asJava, Typed.fromDetailedType[java.util.Map[String, Any]]),
        (Map(123 -> "foo").asJava, Typed.fromDetailedType[java.util.Map[Int, Any]]),
      )
    ) { case (givenMap, givenType) =>
      val encodedJson  = ToJsonEncoder.default.encodeUnsafe(givenMap)
      val decodedValue = FromJsonTypingResultBasedDecoder.decodeValue(givenType, encodedJson.hcursor).rightValue
      decodedValue shouldBe givenMap
    }
  }

}
