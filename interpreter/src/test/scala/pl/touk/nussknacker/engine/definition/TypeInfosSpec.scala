package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos.{FunctionalMethodInfo, MethodInfo, Parameter, SerializableMethodInfo, StaticMethodInfo}

class TypeInfosSpec extends FunSuite with Matchers {
  test("should create methodInfos without varArgs") {
    StaticMethodInfo.fromParameterList(List(), Unknown, "", None, varArgs = false) shouldBe
      StaticMethodInfo(List(), None, Unknown, "", None)
  }

  test("should create methodInfos with varArgs") {
    StaticMethodInfo.fromParameterList(List(Parameter("", Typed[Array[Object]])), Unknown, "", None, varArgs = true) shouldBe
      StaticMethodInfo(List(), Some(Parameter("", Unknown)), Unknown, "", None)
  }

  test("should throw errors when creating illegal method") {
    intercept[AssertionError] {
      StaticMethodInfo.fromParameterList(List(Parameter("", Typed[Int])), Unknown, "", None, varArgs = true)
    }
    intercept[AssertionError] {
      StaticMethodInfo.fromParameterList(List(), Unknown, "", None, varArgs = true)
    }
  }

  test("should generate serializable method info") {
    val paramX = Parameter("x", Typed[Int])
    val paramY = Parameter("y", Typed[String])
    val paramYArray = Parameter("y", Typed.genericTypeClass[Array[Object]](List(Typed[String])))
    def f(x: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = Unknown.validNel

    StaticMethodInfo(List(paramX), None, Typed[Double], "b", Some("c")).serializable shouldBe
      SerializableMethodInfo(List(paramX), Typed[Double], Some("c"), varArgs = false)
    StaticMethodInfo(List(paramX), Some(paramY), Typed[Long], "d", Some("e")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramYArray), Typed[Long], Some("e"), varArgs = true)
    FunctionalMethodInfo(f, StaticMethodInfo(List(paramX, paramY), None, Typed[String], "f", Some("g"))).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramY), Typed[String], Some("g"), varArgs = false)
  }

  private val noVarArgsMethodInfo =
    StaticMethodInfo(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), None, Typed[Double], "f", None)
  private val varArgsMethodInfo =
    StaticMethodInfo(List(Parameter("", Typed[String])), Some(Parameter("", Typed[Int])), Typed[Float], "f", None)
  private val superclassMethodInfo =
    StaticMethodInfo(List(Parameter("", Unknown)), Some(Parameter("", Typed[Number])), Typed[String], "f", None)

  private def checkApply(info: MethodInfo,
                         args: List[TypingResult],
                         expected: ValidatedNel[String, TypingResult]): Unit =
    info.computeResultType(args).leftMap(_.map(_.message)) shouldBe expected

  private def checkApplyValid(info: MethodInfo,
                              args: List[TypingResult],
                              expected: TypingResult): Unit =
    checkApply(info, args, expected.validNel)

  private def checkApplyInvalid(info: MethodInfo,
                                args: List[TypingResult],
                                expected: ExpressionParseError): Unit =
    checkApply(info, args, expected.message.invalidNel)

  test("should generate type functions for methods without varArgs") {
    def noVarArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(noVarArgsMethodInfo, args, Typed[Double])
    def noVarArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(noVarArgsMethodInfo, args, new ArgumentTypeError(
        new Signature(noVarArgsMethodInfo.name, args, None),
        new Signature(noVarArgsMethodInfo.name, noVarArgsMethodInfo.staticNoVarArgParameters.map(_.refClazz), None) :: Nil
      ))

    noVarArgsCheckValid(List(Typed[Int], Typed[String]))

    noVarArgsCheckInvalid(List())
    noVarArgsCheckInvalid(List(Typed[Int], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[String], Typed[Double]))
    noVarArgsCheckInvalid(List(Typed[Int], Typed[String], Typed[Double]))
  }

  test("should generate type functions for methods with varArgs") {
    def varArgsCheckValid(args: List[TypingResult]): Unit =
      checkApplyValid(varArgsMethodInfo, args, Typed[Float])
    def varArgsCheckInvalid(args: List[TypingResult]): Unit =
      checkApplyInvalid(varArgsMethodInfo, args, new ArgumentTypeError(
        new Signature(varArgsMethodInfo.name, args, None),
        new Signature(
          varArgsMethodInfo.name,
          varArgsMethodInfo.staticNoVarArgParameters.map(_.refClazz),
          varArgsMethodInfo.staticVarArgParameter.map(_.refClazz)
        ) :: Nil
      ))

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
    checkApplyValid(superclassMethodInfo, List(Typed[String], Typed[Int], Typed[Double], Typed[Number]), Typed[String])
  }
}
