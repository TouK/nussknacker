package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.ui.process.test.testcase.SpelValuePrettyPrinter.prettyPrintValue

class SpelValuePrettyPrinterSpec extends AnyFunSuite with Matchers {

  test("print like a SpEL literal values") {
    forAll(
      Table(
        ("valueToPrint", "expectedOutput"),
        (java.util.List.of("a", "b", "c"), "{a, b, c}"),
        (java.util.List.of[java.util.List[String]](java.util.List.of[String]("a")), "{{a}}"),
        (java.util.List.of(), "{}"),
        (java.util.Map.of("a", 1234), "{a: 1234}"),
        (java.util.Map.of("a", java.util.Map.of("b", 1234)), "{a: {b: 1234}}"),
        (java.util.Map.of(), "{:}"),
        (Array("a", "b", "c"), "{a, b, c}"), // in spel there is no array literal so we print it as a list literal,
        (Array(Array("a")), "{{a}}"),
        (Array[String](), "{}"),
      )
    ) { (valueToPrint, expectedOutput) =>
      prettyPrintValue(valueToPrint) shouldBe expectedOutput
    }
  }

}
