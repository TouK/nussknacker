package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.ui.process.test.testcase.SpelValuePrettyPrinter.prettyPrintValue

class SpelValuePrettyPrinterSpec extends AnyFunSuite with Matchers {

  test("print like a SpEL literal values") {
    val examples = Table(
      ("valueToPrint", "expectedOutput"),

      // Primitives
      (null, "null"),
      ("simple string", "'simple string'"),
      ("string with 'quote'", "'string with ''quote'''"),
      (123, "123"),
      (123.45, "123.45"),
      (99L, "99L"),
      (true, "true"),
      (false, "false"),

      // Collections
      (java.util.List.of("a", "b", "c"), "{'a', 'b', 'c'}"),
      (java.util.List.of(java.util.List.of("a")), "{{'a'}}"),
      (java.util.List.of(), "{}"),
      (java.util.Map.of("a", 1234), "{'a': 1234}"),
      (java.util.Map.of("a", java.util.Map.of("b", 1234)), "{'a': {'b': 1234}}"),
      (java.util.Map.of(), "{:}"),
      (Array("a", "b", "c"), "{'a', 'b', 'c'}"),
      (Array(Array("a")), "{{'a'}}"),
      (Array[String](), "{}"),
      (java.util.List.of("text", 42, true), "{'text', 42, true}")
    )

    forAll(examples) { (valueToPrint, expectedOutput) =>
      prettyPrintValue(valueToPrint) shouldBe expectedOutput
    }
  }

}
