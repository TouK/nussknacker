package pl.touk.nussknacker.engine.api.typed

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.typed.ConversionNecessaryForTypeAssignment.{
  ConvertionIsNeeded,
  NoConvertionIsNeeded
}
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.{Loose, Strict}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}

import java.time.ZoneId

class AssignabilityDeterminerSpec extends AnyFunSuite with Matchers {

  val wideningConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (Typed[Int], Typed[Int], Valid(NoConvertionIsNeeded), Valid(NoConvertionIsNeeded)),
    (Typed[Int], Typed[Double], Valid(ConvertionIsNeeded(Typed[Double])), Valid(ConvertionIsNeeded(Typed[Double]))),
    (Typed[Int], Unknown, Valid(NoConvertionIsNeeded), Valid(NoConvertionIsNeeded)),
    (
      Typed.fromDetailedType[java.util.List[Int]],
      Unknown,
      Valid(NoConvertionIsNeeded),
      Valid(NoConvertionIsNeeded)
    ),
    (
      Typed.fromDetailedType[java.util.List[Int]],
      Typed.fromDetailedType[java.util.List[Int]],
      Valid(NoConvertionIsNeeded),
      Valid(NoConvertionIsNeeded)
    ),
    (
      Typed.fromDetailedType[java.util.List[Int]],
      Typed.fromDetailedType[java.util.List[Any]],
      Valid(NoConvertionIsNeeded),
      Valid(NoConvertionIsNeeded)
    ),
    (
      Typed.fromDetailedType[java.util.Map[String, Int]],
      Typed.fromDetailedType[java.util.Map[String, Int]],
      Valid(NoConvertionIsNeeded),
      Valid(NoConvertionIsNeeded)
    ),
    (
      Typed.fromDetailedType[java.util.Map[String, Int]],
      Typed.fromDetailedType[java.util.Map[String, Any]],
      Valid(NoConvertionIsNeeded),
      Valid(NoConvertionIsNeeded)
    ),
    (
      Typed.fromDetailedType[java.util.Map[String, Int]],
      Typed.fromDetailedType[java.util.Map[Any, Int]],
      Invalid(NonEmptyList.of("Key types of Maps String and Unknown are not equals")),
      Valid(NoConvertionIsNeeded)
    ),
  )

  test("isAssignable with Strict ConversionStrategy should pass for widening cases") {
    forAll(wideningConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Strict)
      result shouldBe expectedStrict
    }
  }

  test("isAssignable with Loose ConversionStrategy should pass for widening cases") {
    forAll(wideningConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Loose)
      result shouldBe expectedLoose
    }
  }

  val narrowingConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (Typed[Long], Typed[Int], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[Long], Typed[Short], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[Double], Typed[Float], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[BigDecimal], Typed[Double], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[Any], Typed[Int], Invalid(NonEmptyList.of("")), Valid(())),
    (
      Typed.fromDetailedType[java.util.List[Any]],
      Typed.fromDetailedType[java.util.List[Int]],
      Invalid(NonEmptyList.of("")),
      Valid(())
    ),
    (
      Typed.fromDetailedType[java.util.Map[String, Any]],
      Typed.fromDetailedType[java.util.Map[String, Int]],
      Invalid(NonEmptyList.of("")),
      Valid(())
    ),
  )

  test("isAssignable with Strict ConversionStrategy should fail for narrowing cases") {
    forAll(narrowingConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Strict)
      result match {
        case Valid(_) if expectedStrict.isValid     => succeed
        case Invalid(_) if expectedStrict.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

  test("isAssignable with Loose ConversionStrategy should pass for narrowing cases") {
    forAll(narrowingConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Loose)
      result match {
        case Valid(_) if expectedLoose.isValid     => succeed
        case Invalid(_) if expectedLoose.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

  val implicitConversionCases = Table(
    ("sourceType", "targetType"),
    (Typed.fromInstance("UTC"), Typed[ZoneId]),
    (Typed.fromInstance(Array(1, 2, 3)), Typed.fromDetailedType[java.util.List[Int]]),
    (Typed.fromInstance(java.math.BigInteger.ONE), Typed[java.math.BigDecimal]),
    (Typed.fromInstance(123), Typed[java.math.BigDecimal]),
    (Typed.fromInstance(123), Typed[java.math.BigInteger]),
  )

  test("isAssignable with Strict ConversionStrategy should fail for implicit conversions") {
    forAll(implicitConversionCases) { (sourceType, targetType) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Strict)
      result shouldBe Symbol("invalid")
    }
  }

  test("isAssignable with Loose ConversionStrategy should pass for implicit conversions") {
    forAll(implicitConversionCases) { (sourceType, targetType) =>
      val result = AssignabilityDeterminer.isAssignable(sourceType, targetType)(Loose)
      result shouldBe Symbol("valid")
    }
  }

}
