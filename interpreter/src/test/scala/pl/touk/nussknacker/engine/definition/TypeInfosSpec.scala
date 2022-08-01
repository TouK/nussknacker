package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, NoVarArgSignature, ExpressionParseError, VarArgSignature}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos.{FunctionalMethodInfo, MethodInfo, NoVarArgsMethodInfo, Parameter, SerializableMethodInfo, VarArgsMethodInfo}

class TypeInfosSpec extends FunSuite with Matchers {
  test("should create methodInfos without varArgs") {
    MethodInfo(List(), Unknown, "", None, varArgs = false) shouldBe
      NoVarArgsMethodInfo(List(), Unknown, "", None)
  }

  test("should create methodInfos with varArgs") {
    MethodInfo(List(Parameter("", Typed[Array[Object]])), Unknown, "", None, varArgs = true) shouldBe
      VarArgsMethodInfo(List(), Parameter("", Unknown), Unknown, "", None)
  }

  test("should throw errors when creating illegal method") {
    intercept[AssertionError] { MethodInfo(List(Parameter("", Typed[Int])), Unknown, "", None, varArgs = true) }
    intercept[AssertionError] { MethodInfo(List(), Unknown, "", None, varArgs = true) }
  }

  test("should generate serializable method info") {
    val paramX = Parameter("x", Typed[Int])
    val paramY = Parameter("y", Typed[String])
    val paramYArray = Parameter("y", Typed.genericTypeClass[Array[Object]](List(Typed[String])))
    def f(x: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = Unknown.validNel

    NoVarArgsMethodInfo(List(paramX), Typed[Double], "b", Some("c")).serializable shouldBe
      SerializableMethodInfo(List(paramX), Typed[Double], Some("c"), varArgs = false)
    VarArgsMethodInfo(List(paramX), paramY, Typed[Long], "d", Some("e")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramYArray), Typed[Long], Some("e"), varArgs = true)
    FunctionalMethodInfo(f, List(paramX, paramY), Typed[String], Some("g")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramY), Typed[String], Some("g"), varArgs = false)
  }

  private val noVarArgsMethodInfo =
    NoVarArgsMethodInfo(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), Typed[Double], "f", None)
  private val varArgsMethodInfo =
    VarArgsMethodInfo(List(Parameter("", Typed[String])), Parameter("", Typed[Int]), Typed[Float], "f", None)
  private val superclassMethodInfo =
    VarArgsMethodInfo(List(Parameter("", Unknown)), Parameter("", Typed[Number]), Typed[String], "f", None)

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
        new NoVarArgSignature(noVarArgsMethodInfo.name, args),
        List(new NoVarArgSignature(noVarArgsMethodInfo.name, noVarArgsMethodInfo.staticParameters.map(_.refClazz)))
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
        new NoVarArgSignature(varArgsMethodInfo.name, args),
        List(new VarArgSignature(varArgsMethodInfo.name, varArgsMethodInfo.noVarParameters.map(_.refClazz), varArgsMethodInfo.varParameter.refClazz))
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
