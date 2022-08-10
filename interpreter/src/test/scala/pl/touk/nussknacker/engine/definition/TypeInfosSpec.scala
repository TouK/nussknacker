package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, Parameter, ParameterList, Signature}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos.{FunctionalMethodInfo, MethodInfo, SerializableMethodInfo, StaticMethodInfo}

class TypeInfosSpec extends FunSuite with Matchers {
  test("should generate serializable method info") {
    val paramX = Parameter("x", Typed[Int])
    val paramY = Parameter("y", Typed[String])
    val paramYArray = Parameter("y", Typed.genericTypeClass[Array[Object]](List(Typed[String])))
    def f(x: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = Unknown.validNel

    StaticMethodInfo(ParameterList(List(paramX), None), Typed[Double], "b", Some("c")).serializable shouldBe
      SerializableMethodInfo(List(paramX), Typed[Double], Some("c"), varArgs = false)
    StaticMethodInfo(ParameterList(List(paramX), Some(paramY)), Typed[Long], "d", Some("e")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramYArray), Typed[Long], Some("e"), varArgs = true)
    FunctionalMethodInfo(f, StaticMethodInfo(ParameterList(List(paramX, paramY), None), Typed[String], "f", Some("g"))).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramY), Typed[String], Some("g"), varArgs = false)
  }

  private val noVarArgsMethodInfo =
    StaticMethodInfo(ParameterList(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), None), Typed[Double], "f", None)
  private val varArgsMethodInfo =
    StaticMethodInfo(ParameterList(List(Parameter("", Typed[String])), Some(Parameter("", Typed[Int]))), Typed[Float], "f", None)
  private val superclassMethodInfo =
    StaticMethodInfo(ParameterList(List(Parameter("", Unknown)), Some(Parameter("", Typed[Number]))), Typed[String], "f", None)

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
        Signature(noVarArgsMethodInfo.name, args, None),
        Signature(noVarArgsMethodInfo.name, noVarArgsMethodInfo.staticNoVarArgParameters.map(_.refClazz), None) :: Nil
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
        Signature(varArgsMethodInfo.name, args, None),
        Signature(
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
