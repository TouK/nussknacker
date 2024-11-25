package pl.touk.nussknacker.engine.api.typed

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.typed._

class AssignabilityDeterminerSpec extends AnyFunSuite with Matchers {

  // Test data table: (sourceType, targetType, expectedStrict, expectedLoose)
  val strictConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (typing.Typed[Int], typing.Typed[Int], Valid(()), Valid(())),                           // Same primitive type
    (typing.Typed[Int], typing.Typed[Double], Valid(()), Valid(())),                        // Primitive widening
    (typing.Typed[List[Int]], typing.Typed[List[Int]], Valid(()), Valid(())),               // Same generic type
    (typing.Typed[List[Int]], typing.Typed[List[Any]], Valid(()), Valid(())),               // Generic type variance
    (typing.Typed[Map[String, Int]], typing.Typed[Map[String, Int]], Valid(()), Valid(())), // Same map type
    (typing.Typed[Map[String, Int]], typing.Typed[Map[Any, Any]], Valid(()), Valid(()))
  ) // Different records

  test("isAssignableStrict should pass for strict cases") {
    forAll(strictConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignableStrict(sourceType, targetType)
      result shouldBe expectedStrict
    }
  }

  test("isAssignableLoose should pass for strict cases") {
    forAll(strictConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignableLoose(sourceType, targetType)
      result shouldBe expectedLoose
    }
  }

  val looseConversionCases = Table(
    ("sourceType", "targetType", "expectedStrict", "expectedLoose"),
    (typing.Typed[Long], typing.Typed[Int], Invalid(NonEmptyList.of("")), Valid(())),
    (typing.Typed[Double], typing.Typed[Int], Invalid(NonEmptyList.of("")), Invalid(NonEmptyList.of(""))),
    (typing.Typed[BigDecimal], typing.Typed[Int], Invalid(NonEmptyList.of("")), Invalid(NonEmptyList.of("")))
  )

  test("isAssignableStrict should fail for looser numerical cases") {
    forAll(looseConversionCases) { (sourceType, targetType, expectedStrict, _) =>
      val result = AssignabilityDeterminer.isAssignableStrict(sourceType, targetType)
      result match {
        case Valid(_) if expectedStrict.isValid     => succeed
        case Invalid(_) if expectedStrict.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

  test("isAssignableLoose should pass for looser cases") {
    forAll(looseConversionCases) { (sourceType, targetType, _, expectedLoose) =>
      val result = AssignabilityDeterminer.isAssignableLoose(sourceType, targetType)
      result match {
        case Valid(_) if expectedLoose.isValid     => succeed
        case Invalid(_) if expectedLoose.isInvalid => succeed
        case _ => fail(s"Unexpected result: $result for types $sourceType -> $targetType")
      }
    }
  }

}
