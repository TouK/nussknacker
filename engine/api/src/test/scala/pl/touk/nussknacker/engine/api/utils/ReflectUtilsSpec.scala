package pl.touk.nussknacker.engine.api.utils

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.util.ReflectUtils


class ReflectUtilsSpec extends FunSuite with Matchers {

  test("simpleNameWithoutSuffix on class") {
    ReflectUtils.simpleNameWithoutSuffix((new TestClass).getClass) should equal("TestClass")
  }

  test("simpleNameWithoutSuffix on object") {
    ReflectUtils.simpleNameWithoutSuffix(TestObject.getClass) should equal("TestObject")
  }

  test("simpleNameWithoutSuffix on companion object") {
    ReflectUtils.simpleNameWithoutSuffix(TestCompanionObject.getClass) should equal("TestCompanionObject")
  }

  test("simpleNameWithoutSuffix on class with companion object") {
    ReflectUtils.simpleNameWithoutSuffix((new TestCompanionObject).getClass) should equal("TestCompanionObject")
  }

}

class TestClass {}

object TestObject {}

object TestCompanionObject {}

class TestCompanionObject {}
