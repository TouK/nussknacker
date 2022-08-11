package pl.touk.nussknacker.engine.api.generics

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

class ParameterSpec extends FunSuite with Matchers {
  private def checkConversions(args: List[TypingResult], varArg: Option[TypingResult]) = {
    val noVarArgParameters = args.zipWithIndex.map{ case (typ, i) => Parameter(i.toString, typ) }
    val varArgParameterFull = varArg.map(x => Parameter("v", Typed.genericTypeClass[Array[Object]](x :: Nil)))
    val varArgParameterSmall = varArg.map(Parameter("v", _))

    val parameterList = noVarArgParameters ::: varArgParameterFull.toList

    MethodTypeInfo.fromList(parameterList, varArg.isDefined, Unknown) shouldBe MethodTypeInfo(noVarArgParameters, varArgParameterSmall, Unknown)
    MethodTypeInfo(noVarArgParameters, varArgParameterSmall, Unknown).parametersToList shouldBe parameterList
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
      MethodTypeInfo.fromList(Nil, varArgs = true, Unknown)
    }.getMessage shouldBe "Method with varArgs must have at least one parameter"
    intercept[AssertionError] {
      MethodTypeInfo.fromList(Parameter("", Typed[Int]) :: Nil, varArgs = true, Unknown)
    }.getMessage shouldBe "VarArg must have type of array"
  }
}
