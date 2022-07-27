package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.generics.SpelParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfo.{FunctionalMethodInfo, MethodInfo, NoVarArgsMethodInfo, Parameter, SerializableMethodInfo, VarArgsMethodInfo}

class TypeInfoSpec extends FunSuite with Matchers {
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
    def f(x: List[TypingResult]): ValidatedNel[SpelParseError, TypingResult] = Unknown.validNel

    NoVarArgsMethodInfo(List(paramX), Typed[Double], "b", Some("c")).serializable shouldBe
      SerializableMethodInfo(List(paramX), Typed[Double], Some("c"), varArgs = false)
    VarArgsMethodInfo(List(paramX), paramY, Typed[Long], "d", Some("e")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramYArray), Typed[Long], Some("e"), varArgs = true)
    FunctionalMethodInfo(f, List(paramX, paramY), Typed[String], "f", Some("g")).serializable shouldBe
      SerializableMethodInfo(List(paramX, paramY), Typed[String], Some("g"), varArgs = false)
  }

  private val noVarArgsMethodInfo =
    NoVarArgsMethodInfo(List(Parameter("", Typed[Int]), Parameter("", Typed[String])), Typed[Double], "", None)
  private val varArgsMethodInfo =
    VarArgsMethodInfo(List(Parameter("", Typed[String])), Parameter("", Typed[Int]), Typed[Float], "", None)
  private val superclassMethodInfo =
    VarArgsMethodInfo(List(Parameter("", Unknown)), Parameter("", Typed[Number]), Typed[String], "", None)

  // FIXME: Add expected result for other types.
  test("should generate type functions for methods without varArgs") {
    noVarArgsMethodInfo.apply(List(Typed[Int], Typed[String])) shouldBe Typed[Double].validNel

    noVarArgsMethodInfo.apply(List()) shouldBe ""
    noVarArgsMethodInfo.apply(List(Typed[Int], Typed[Double])) shouldBe ""
    noVarArgsMethodInfo.apply(List(Typed[String], Typed[Double])) shouldBe ""
    noVarArgsMethodInfo.apply(List(Typed[Int], Typed[String], Typed[Double])) shouldBe ""
  }

  test("should generate type functions for methods with varArgs") {
    varArgsMethodInfo.apply(List(Typed[String])) shouldBe Typed[Float].validNel
    varArgsMethodInfo.apply(List(Typed[String], Typed[Int])) shouldBe Typed[Float].validNel
    varArgsMethodInfo.apply(List(Typed[String], Typed[Int], Typed[Int], Typed[Int])) shouldBe Typed[Float].validNel

    varArgsMethodInfo.apply(List()) shouldBe ""
    varArgsMethodInfo.apply(List(Typed[Int])) shouldBe ""
    varArgsMethodInfo.apply(List(Typed[String], Typed[String])) shouldBe ""
    varArgsMethodInfo.apply(List(Typed[String], Typed[Int], Typed[Double])) shouldBe ""
    varArgsMethodInfo.apply(List(Typed[Int], Typed[Int])) shouldBe ""
  }

  test("should accept subclasses as arguments to methods") {
    superclassMethodInfo.apply(List(Typed[String], Typed[Int], Typed[Double], Typed[Number])) shouldBe
      Typed[String].validNel
  }
}
