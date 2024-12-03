package pl.touk.nussknacker.engine.api.typed

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class AssignabilityDeterminerSpec extends AnyFunSuite with Matchers {

  val wideningConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (Typed[Int], Typed[Int], Valid(()), Valid(())),
    (Typed[Int], Typed[Double], Valid(()), Valid(())),
    (Typed[List[Int]], Typed[List[Int]], Valid(()), Valid(())),
    (Typed[List[Int]], Typed[List[Any]], Valid(()), Valid(())),
    (Typed[Map[String, Int]], Typed[Map[String, Int]], Valid(()), Valid(())),
    (Typed[Map[String, Int]], Typed[Map[Any, Any]], Valid(()), Valid(()))
  )

  test("isAssignableStrict should pass for widening cases") {
    forAll(wideningConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignableStrict(sourceType, targetType)
      result shouldBe expectedStrict
    }
  }

  test("isAssignableLoose should pass for widening cases") {
    forAll(wideningConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignableLoose(sourceType, targetType)
      result shouldBe expectedLoose
    }
  }

  val narrowingConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (Typed[Long], Typed[Int], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[Long], Typed[Short], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[Double], Typed[Float], Invalid(NonEmptyList.of("")), Valid(())),
    (Typed[BigDecimal], Typed[Double], Invalid(NonEmptyList.of("")), Valid(()))
  )

  test("isAssignableStrict should fail for narrowing numerical cases") {
    forAll(narrowingConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignableStrict(sourceType, targetType)
      result match {
        case Valid(_) if expectedStrict.isValid     => succeed
        case Invalid(_) if expectedStrict.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

  test("isAssignableLoose should pass for narrowing cases") {
    forAll(narrowingConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignableLoose(sourceType, targetType)
      result match {
        case Valid(_) if expectedLoose.isValid     => succeed
        case Invalid(_) if expectedLoose.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

}
