package pl.touk.nussknacker.engine.api.generics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

class ParameterSpec extends AnyFunSuite with Matchers {
  private def checkConversions(args: List[TypingResult], varArg: Option[TypingResult]) = {
    val noVarArgParameters = args.zipWithIndex.map{ case (typ, i) => Parameter(i.toString, typ) }
    val varArgParameterFull = varArg.map(x => Parameter("v", Typed.genericTypeClass[Array[Object]](x :: Nil)))
    val varArgParameterSmall = varArg.map(Parameter("v", _))

    val parameterList = noVarArgParameters ::: varArgParameterFull.toList

    ParameterList.fromList(parameterList, varArg.isDefined) shouldBe ParameterList(noVarArgParameters, varArgParameterSmall)
    ParameterList(noVarArgParameters, varArgParameterSmall).toList shouldBe parameterList
  }

  test("should create methodInfos without varArgs") {
    checkConversions(Nil, None)
    checkConversions(Typed[Int] :: Nil, None)
    checkConversions(Typed[String] :: Typed[Double] :: Typed[Long] :: Nil, None)
  }

  test("should create methodInfos with varArgs") {
    checkConversions(Nil, Some(Typed[Int]))
    checkConversions(Typed[Double] :: Nil, Some(Typed[Int]))
    checkConversions(Typed[Double] :: Typed[String] :: Typed[Int] :: Nil, Some(Typed[Double]))
  }

  test("should throw errors when creating illegal method") {
    intercept[AssertionError] {
      ParameterList.fromList(Nil, varArgs = true)
    }.getMessage shouldBe "Method with varArgs must have at least one parameter"
    intercept[AssertionError] {
      ParameterList.fromList(Parameter("", Typed[Int]) :: Nil, varArgs = true)
    }.getMessage shouldBe "VarArg must have type of array"
  }
}
