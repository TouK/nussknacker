package pl.touk.nussknacker.engine.definition.clazz

import cats.implicits.catsSyntaxValidatedId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.generics.{MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class MethodTypeInfoSubclassCheckerSpec extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private def check(
      subclassNoVarArgs: List[TypingResult],
      subclassVarArg: Option[TypingResult],
      superclassNoVarArgs: List[TypingResult],
      superclassVarArg: Option[TypingResult],
      expectedErrors: List[ParameterListError]
  ): Unit = {
    def toMethodTypeInfo(noVarArgs: List[TypingResult], varArg: Option[TypingResult]): MethodTypeInfo =
      MethodTypeInfo(noVarArgs.map(Parameter("", _)), varArg.map(Parameter("", _)), Unknown)

    val checkResult = MethodTypeInfoSubclassChecker.check(
      toMethodTypeInfo(subclassNoVarArgs, subclassVarArg),
      toMethodTypeInfo(superclassNoVarArgs, superclassVarArg)
    )

    expectedErrors match {
      case Nil => checkResult shouldBe ().validNel
      case lst => checkResult.invalidValue.toList should contain theSameElementsAs lst
    }
  }

  test("should work when subclass and superclass have no varArgs") {
    val table = Table(
      ("subclassNoVarArgs", "subclassVarArg", "superclassNoVarArgs", "superclassVarArg", "expected errors"),
      (List(Typed[Int], Typed[String]), None, List(Typed[Number], Typed[String]), None, Nil),
      (List(Typed[Double]), None, List(Typed[Int]), None, List(NotSubclassArgument(1, Typed[Double], Typed[Int]))),
      (List(Typed[Int], Typed[String]), None, List(Typed[Int]), None, List(WrongNumberOfArguments(2, 1))),
      (List(), None, List(Typed[Int]), None, List(WrongNumberOfArguments(0, 1)))
    )
    forAll(table)(check)
  }

  test("should work when only subclass has varArgs") {
    val table = Table(
      ("subclassNoVarArgs", "subclassVarArg", "superclassNoVarArgs", "superclassVarArg", "expected errors"),
      (List(), Some(Typed[Int]), List(Typed[Int]), None, List(BadVarArg, WrongNumberOfArguments(0, 1))),
      (
        List(),
        Some(Typed[String]),
        List(Typed[String], Typed[String]),
        None,
        List(BadVarArg, WrongNumberOfArguments(0, 2))
      ),
      (List(Typed[Int]), Some(Typed[Int]), List(Typed[Int]), None, List(BadVarArg)),
      (
        List(Typed[Int]),
        Some(Typed[String]),
        List(Typed[Int], Typed[Int]),
        None,
        List(BadVarArg, WrongNumberOfArguments(1, 2))
      )
    )
    forAll(table)(check)
  }

  test("should work when only superclass has varArgs") {
    val table = Table(
      ("subclassNoVarArgs", "subclassVarArg", "superclassNoVarArgs", "superclassVarArg", "expected errors"),
      (List(Typed[Int]), None, List(), Some(Typed[Int]), Nil),
      (List(), None, List(), Some(Typed[String]), Nil),
      (List(Typed[String], Typed[Long], Typed[Double]), None, List(Typed[String]), Some(Typed[Number]), Nil),
      (List(Typed[Long]), None, List(Typed[Long], Typed[String]), Some(Typed[Int]), List(NotEnoughArguments(1, 2))),
      (
        List(Typed[Int], Typed[String], Typed[Double]),
        None,
        List(),
        Some(Typed[Number]),
        List(NotSubclassArgument(2, Typed[String], Typed[Number]))
      )
    )
    forAll(table)(check)
  }

  test("should work when subclass and superclass have varArgs") {
    val table = Table(
      ("subclassNoVarArgs", "subclassVarArg", "superclassNoVarArgs", "superclassVarArg", "expected errors"),
      (List(Typed[Double], Typed[String]), Some(Typed[Int]), List(Typed[Number], Unknown), Some(Typed[Int]), Nil),
      (List(Typed[Int], Typed[Double]), Some(Typed[Long]), List(Typed[Int]), Some(Typed[Number]), Nil),
      (
        List(Typed[Int]),
        Some(Typed[String]),
        List(Typed[Int], Typed[String]),
        Some(Typed[String]),
        List(NotEnoughArguments(1, 2))
      ),
      (List(), Some(Typed[Long]), List(), Some(Typed[String]), List(NotSubclassVarArgument(Typed[Long], Typed[String])))
    )
    forAll(table)(check)
  }

  test("should work with typed maps") {
    check(
      List(Typed.record(Map("a" -> Typed[String], "b" -> Typed[String]))),
      None,
      List(Typed[java.util.Map[_, _]]),
      None,
      Nil
    )

    check(
      List(Typed.record(Map("a" -> Typed[Int], "b" -> Typed[Double]))),
      None,
      List(Typed.fromDetailedType[java.util.Map[String, Number]]),
      None,
      Nil
    )
  }

  test("should check return type") {
    MethodTypeInfoSubclassChecker.check(
      MethodTypeInfo(Nil, None, Typed[Long]),
      MethodTypeInfo(Nil, None, Typed[Number])
    ) shouldBe ().validNel

    MethodTypeInfoSubclassChecker.check(
      MethodTypeInfo(Nil, None, Typed[String]),
      MethodTypeInfo(Nil, None, Typed[Int])
    ) shouldBe NotSubclassResult(Typed[String], Typed[Int]).invalidNel
  }

}
