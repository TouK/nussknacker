package pl.touk.nussknacker.engine.types

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{Parameter, ParameterList}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}

class ParameterListSubclassCheckerSpec extends FunSuite with Matchers {
  private def check(subclassNoVarArgs: List[TypingResult],
                    subclassVarArg: Option[TypingResult],
                    superclassNoVarArgs: List[TypingResult],
                    superclassVarArg: Option[TypingResult]): Boolean = {
    def toParameterList(noVarArgs: List[TypingResult], varArg: Option[TypingResult]): ParameterList =
      ParameterList(noVarArgs.map(Parameter("", _)), varArg.map(Parameter("", _)))

    ParameterListSubclassChecker.check(
      toParameterList(subclassNoVarArgs, subclassVarArg),
      toParameterList(superclassNoVarArgs, superclassVarArg)
    )
  }

  test("should work when subclass and superclass have no varArgs") {
    check(List(Typed[Int], Typed[String]), None, List(Typed[Number], Typed[String]), None) shouldBe true

    check(List(Typed[Double]), None, List(Typed[Int]), None) shouldBe false
    check(List(Typed[Int], Typed[String]), None, List(Typed[Int]), None) shouldBe false
    check(List(), None, List(Typed[Int]), None) shouldBe false
  }

  test("should work when only subclass has varArgs") {
    check(List(), Some(Typed[Int]), List(Typed[Int]), None) shouldBe false
    check(List(), Some(Typed[String]), List(Typed[String], Typed[String]), None) shouldBe false
    check(List(Typed[Int]), Some(Typed[Int]), List(Typed[Int]), None) shouldBe false
    check(List(Typed[Int]), Some(Typed[String]), List(Typed[Int], Typed[Int]), None) shouldBe false
  }

  test("should work when only superclass has varArgs") {
    check(List(Typed[Int]), None, List(), Some(Typed[Int])) shouldBe true
    check(List(), None, List(), Some(Typed[String])) shouldBe true
    check(List(Typed[String], Typed[Long], Typed[Double]), None, List(Typed[String]), Some(Typed[Number])) shouldBe true

    check(List(Typed[Long]), None, List(Typed[Long], Typed[String]), Some(Typed[Int])) shouldBe false
    check(List(Typed[Int], Typed[String], Typed[Double]), None, List(), Some(Typed[Number])) shouldBe false
  }

  test("should work when subclass and superclass have varArgs") {
    check(List(Typed[Double], Typed[String]), Some(Typed[Int]), List(Typed[Number], Unknown), Some(Typed[Int])) shouldBe true
    check(List(Typed[Int], Typed[Double]), Some(Typed[Long]), List(Typed[Int]), Some(Typed[Number])) shouldBe true

    check(List(Typed[Int]), Some(Typed[String]), List(Typed[Int], Typed[String]), Some(Typed[String])) shouldBe false
    check(List(), Some(Typed[Long]), List(), Some(Typed[String])) shouldBe false
  }

  test("should work with typed maps") {
    check(
      List(TypedObjectTypingResult(List("a" -> Typed[String], "b" -> Typed[String]))),
      None,
      List(Typed[java.util.Map[_, _]]),
      None
    ) shouldBe true

    check(
      List(TypedObjectTypingResult(List("a" -> Typed[Int], "b" -> Typed[Double]))),
      None,
      List(Typed.fromDetailedType[java.util.Map[String, Number]]),
      None
    ) shouldBe true
  }
}