package pl.touk.nussknacker.engine.util.classes

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassesExtensions

class ExtensionsSpec extends AnyFunSuite with Matchers {

  object A {
    class C {}
  }

  object B {
    class C {}
  }

  class D {}

  test("should return simples names and full name for non unique class names") {
    val ac = classOf[A.C]
    val bc = classOf[B.C]
    val d  = classOf[D]
    Set[Class[_]](ac, bc, d).classesBySimpleNamesRegardingClashes() shouldBe Map(
      "pl.touk.nussknacker.engine.util.classes.ExtensionsSpec$A$C" -> ac,
      "pl.touk.nussknacker.engine.util.classes.ExtensionsSpec$B$C" -> bc,
      "D"                                                          -> d
    )
  }

}
