package pl.touk.nussknacker.engine.definition.clazz

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, MethodTypeInfo, Parameter, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ArgumentTypeError

class MethodDefinitionSpec extends AnyFunSuite with Matchers {

  private val noVarArgsMethodDefinition =
    StaticMethodDefinition(
      MethodTypeInfo(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), None, Typed[Double]),
      "f",
      None
    )

  private val varArgsMethodDefinition =
    StaticMethodDefinition(
      MethodTypeInfo(List(Parameter("", Typed[String])), Some(Parameter("", Typed[Int])), Typed[Float]),
      "f",
      None
    )

  private val superclassMethodDefinition =
    StaticMethodDefinition(
      MethodTypeInfo(List(Parameter("", Unknown)), Some(Parameter("", Typed[Number])), Typed[String]),
      "f",
      None
    )

  private def checkApply(
      definition: MethodDefinition,
      args: List[TypingResult],
      expected: ValidatedNel[String, TypingResult]
  ): Unit =
    definition.computeResultType(Unknown, args).leftMap(_.map(_.message)) shouldBe expected

  private def checkApplyValid(definition: MethodDefinition, args: List[TypingResult], expected: TypingResult): Unit =
    checkApply(definition, args, expected.validNel)

  private def checkApplyInvalid(
      definition: MethodDefinition,
      args: List[TypingResult],
      expected: ExpressionParseError
  ): Unit =
    checkApply(definition, args, expected.message.invalidNel)

  test("should generate type functions for methods without varArgs") {
    def noVarArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(noVarArgsMethodDefinition, args, Typed[Double])
    def noVarArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(
        noVarArgsMethodDefinition,
        args,
        ArgumentTypeError(
          noVarArgsMethodDefinition.name,
          Signature(args, None),
          NonEmptyList.one(Signature(noVarArgsMethodDefinition.signature.noVarArgs.map(_.refClazz), None))
        )
      )

    noVarArgsCheckValid(List(Typed[Int], Typed[String]))

    noVarArgsCheckInvalid(List())
    noVarArgsCheckInvalid(List(Typed[Int], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[String], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[Int], Typed[String], Typed[Double]))
  }

  test("should generate type functions for methods with varArgs") {
    def varArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(varArgsMethodDefinition, args, Typed[Float])
    def varArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(
        varArgsMethodDefinition,
        args,
        ArgumentTypeError(
          varArgsMethodDefinition.name,
          Signature(args, None),
          NonEmptyList.one(
            Signature(
              varArgsMethodDefinition.signature.noVarArgs.map(_.refClazz),
              varArgsMethodDefinition.signature.varArg.map(_.refClazz)
            )
          )
        )
      )

    varArgsCheckValid(List(Typed[String]))
    varArgsCheckValid(List(Typed[String], Typed[Int]))
    varArgsCheckValid(List(Typed[String], Typed[Int], Typed[Int], Typed[Int]))

    varArgsCheckInvalid(List())
    varArgsCheckInvalid(List(Typed[Int]))
    varArgsCheckInvalid(List(Typed[String], Typed[String]))
    varArgsCheckInvalid(List(Typed[String], Typed[Int], Typed[Double]))
    varArgsCheckInvalid(List(Typed[Int], Typed[Int]))
  }

  test("should accept subclasses as arguments to methods") {
    checkApplyValid(
      superclassMethodDefinition,
      List(Typed[String], Typed[Int], Typed[Double], Typed[Number]),
      Typed[String]
    )
  }

  test("should automatically validate arguments of generic functions") {
    val methodDefinition = FunctionalMethodDefinition(
      (_, _) => Typed[Int].validNel,
      MethodTypeInfo(
        Parameter("a", Typed[Int]) :: Parameter("b", Typed[Double]) :: Nil,
        Some(Parameter("c", Typed[String])),
        Typed[Int]
      ),
      "f",
      None
    )

    methodDefinition.computeResultType(Unknown, List(Typed[Int], Typed[Double])) should be valid;
    methodDefinition.computeResultType(Unknown, List(Typed[Int], Typed[Double], Typed[String])) should be valid;
    methodDefinition.computeResultType(
      Unknown,
      List(Typed[Int], Typed[Double], Typed[String], Typed[String])
    ) should be valid

    methodDefinition.computeResultType(Unknown, List(Typed[Int])) should be invalid;
    methodDefinition.computeResultType(Unknown, List(Typed[Int], Typed[String])) should be invalid;
    methodDefinition.computeResultType(Unknown, List(Typed[Double], Typed[Int], Typed[String])) should be invalid;
    methodDefinition.computeResultType(Unknown, List()) should be invalid
  }

  test("should validate result against proper signatures") {
    val sig1 = MethodTypeInfo.withoutVarargs(Parameter("a", Typed[Int]) :: Nil, Typed[Int])
    val sig2 = MethodTypeInfo.withoutVarargs(Parameter("a", Typed[String]) :: Nil, Typed[String])

    val methodDefinition =
      FunctionalMethodDefinition((_, _) => Typed[Int].validNel, NonEmptyList.of(sig1, sig2), "f", None)

    methodDefinition.computeResultType(Unknown, Typed[Int] :: Nil) shouldBe Typed[Int].validNel
    intercept[AssertionError] {
      methodDefinition.computeResultType(Unknown, Typed[String] :: Nil)
    }.getMessage shouldBe "Generic function f returned type Integer that does not match any of declared types (String) when called with arguments (String)"
  }

}
