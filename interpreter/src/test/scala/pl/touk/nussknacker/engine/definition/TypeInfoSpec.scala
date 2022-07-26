package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfo.{FunctionalMethodInfo, MethodInfo, NoVarArgMethodInfo, Parameter, SerializableMethodInfo, VarArgsMethodInfo}

class TypeInfoSpec extends FunSuite with Matchers {
  test("should create methodInfos without varArgs") {
    MethodInfo(List(), Unknown, "", None, varArgs = false) shouldBe NoVarArgMethodInfo(List(), Unknown, "", None)
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
    def f(x: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = ???

    NoVarArgMethodInfo(List(paramX), Typed[Double], "b", Some("c")).serializable shouldBe
      SerializableMethodInfo(List(paramX), Typed[Double], Some("c"), varArgs = false)
    VarArgsMethodInfo(List(paramX), paramY, Typed[Long], "d", Some("e")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramYArray), Typed[Long], Some("e"), varArgs = true)
    FunctionalMethodInfo(f, List(paramX, paramY), Typed[String], "f", Some("g")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramY), Typed[String], Some("g"), varArgs = false)
  }
}
