package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.process.test.testcase.SpelValuePrettyPrinter.prettyPrintValue

class SpelValuePrettyPrinterSpec extends AnyFunSuite with Matchers {

  test("print like a SpEL literal values") {
    prettyPrintValue(java.util.List.of("a", "b", "c")) shouldBe "{a, b, c}"
    prettyPrintValue(java.util.List.of(java.util.List.of("a"))) shouldBe "{{a}}"
    prettyPrintValue(java.util.List.of()) shouldBe "{}"
    prettyPrintValue(java.util.Map.of("a", 1234)) shouldBe "{a: 1234}"
    prettyPrintValue(java.util.Map.of("a", java.util.Map.of("b", 1234))) shouldBe "{a: {b: 1234}}"
    prettyPrintValue(java.util.Map.of()) shouldBe "{:}"
    prettyPrintValue(Array("a", "b", "c")) shouldBe "{a, b, c}" // in spel there is no array literal so we print it as a list literal
    prettyPrintValue(Array(Array("a"))) shouldBe "{{a}}"
    prettyPrintValue(Array[String]()) shouldBe "{}"
  }

}
