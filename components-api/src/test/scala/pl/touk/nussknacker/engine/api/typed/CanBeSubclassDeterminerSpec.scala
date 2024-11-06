package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CanBeSubclassDeterminerSpec extends AnyFunSuite with Matchers {

  test("Should validate assignability for decimal types") {
    CanBeSubclassDeterminer.isAssignable(classOf[java.lang.Long], classOf[java.lang.Integer]) shouldBe false
    CanBeSubclassDeterminer.isAssignable(classOf[Number], classOf[Int]) shouldBe false
    CanBeSubclassDeterminer.isAssignable(classOf[Int], classOf[Short]) shouldBe false

    CanBeSubclassDeterminer.isAssignable(classOf[Int], classOf[Long]) shouldBe true
    CanBeSubclassDeterminer.isAssignable(classOf[Int], classOf[Number]) shouldBe true
    CanBeSubclassDeterminer.isAssignable(classOf[Short], classOf[Int]) shouldBe true
  }

  test("Should validate assignability for numerical types") {
    CanBeSubclassDeterminer.isAssignable(classOf[Long], classOf[Double]) shouldBe true
    CanBeSubclassDeterminer.isAssignable(classOf[Float], classOf[Double]) shouldBe true

    CanBeSubclassDeterminer.isAssignable(classOf[Int], classOf[Float]) shouldBe true
    CanBeSubclassDeterminer.isAssignable(classOf[Long], classOf[Double]) shouldBe true
  }

  // to check if autoboxing lang3 is failing - we can remove our fallback if the lib works properly
  test("Should check if lang3 fails for certain isAssignable cases") {
    ClassUtils.isAssignable(classOf[Integer], classOf[Long], true) shouldBe true
    ClassUtils.isAssignable(classOf[Short], classOf[Integer], true) shouldBe true
  }

}
