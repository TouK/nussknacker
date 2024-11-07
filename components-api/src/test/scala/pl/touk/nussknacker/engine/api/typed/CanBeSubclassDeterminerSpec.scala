package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CanBeSubclassDeterminerSpec extends AnyFunSuite with Matchers {

  test("Should validate assignability for decimal types") {
    ImplicitConversionDeterminer.isAssignable(classOf[java.lang.Long], classOf[java.lang.Integer]) shouldBe false
    ImplicitConversionDeterminer.isAssignable(classOf[Number], classOf[Integer]) shouldBe false
    ImplicitConversionDeterminer.isAssignable(classOf[Integer], classOf[java.lang.Short]) shouldBe false

    ImplicitConversionDeterminer.isAssignable(classOf[Integer], classOf[java.lang.Long]) shouldBe true
    ImplicitConversionDeterminer.isAssignable(classOf[Integer], classOf[Number]) shouldBe true
    ImplicitConversionDeterminer.isAssignable(classOf[java.lang.Short], classOf[Integer]) shouldBe true
  }

  test("Should validate assignability for numerical types") {
    ImplicitConversionDeterminer.isAssignable(classOf[java.lang.Long], classOf[java.lang.Double]) shouldBe true
    ImplicitConversionDeterminer.isAssignable(classOf[java.lang.Float], classOf[Double]) shouldBe true

    ImplicitConversionDeterminer.isAssignable(classOf[Integer], classOf[java.lang.Float]) shouldBe true
    ImplicitConversionDeterminer.isAssignable(classOf[java.lang.Long], classOf[java.lang.Double]) shouldBe true
  }

  // to check if autoboxing lang3 is failing - we can remove our fallback from SubclassDeterminer.isAssignable if the lib works properly
  test("Should check if lang3 fails for certain isAssignable cases") {
    ClassUtils.isAssignable(
      classOf[Integer],
      classOf[java.lang.Long],
      true
    ) shouldBe false // should be true in reality, but currently the lib is bugged
    ClassUtils.isAssignable(
      classOf[java.lang.Short],
      classOf[Integer],
      true
    ) shouldBe false // should be true in reality, but currently the lib is bugged
  }

}
