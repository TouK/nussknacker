package pl.touk.nussknacker.engine.definition

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfo.{MethodInfo, NoVarArgMethodInfo, Parameter, VarArgsMethodInfo}

class TypeInfoSpec extends FunSuite with Matchers {
  test("should create methodInfos without varArgs") {
    MethodInfo(List(), Unknown, "", None, varArgs = false) shouldBe NoVarArgMethodInfo(List(), Unknown, "", None)
  }

  test("should create methodInfos with varArgs") {
    MethodInfo(List(Parameter("", Typed[Array[Object]])), Unknown, "", None, varArgs = true) shouldBe
      VarArgsMethodInfo(List(), Parameter("", Unknown), Unknown, "", None)
  }
}
