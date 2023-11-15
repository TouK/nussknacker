package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.CollectionConverters._

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

  test("MandatoryParameterValidator") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, false),
        ("  ", null, false),
        ("\t", null, false),
        (" \n ", null, false),
        ("null", null, true),
        ("#input.foo['bar']", null, true),
        ("true", true, true),
        ("''", "", true),
        ("'foo'", "foo", true),
        ("1", 1, true),
        ("1 + 1", 2, true),
      )
    ) { (expression, value, expected) =>
      MandatoryParameterValidator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("NonNullParameterValidator") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, false),
        ("  ", null, false),
        ("\t", null, false),
        (" \n ", null, false),
        ("null", null, false),
        ("#input.foo['bar']", null, false),
        ("true", true, true),
        ("''", "", true),
        ("'foo'", "foo", true),
        ("1", 1, true),
        ("1 + 1", 2, true),
      )
    ) { (expression, value, expected) =>
      NotNullParameterValidator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("NotBlankParameterValidator") {
    forAll(
      Table(
        ("inputExpression", "isValid"),
        (null, true),
        ("", false),
        ("  ", false),
        ("\t", false),
        (" \n ", false),
        ("someString ", true),
        ("'someString' ", true),
        ("\"someString\" ", true),
        ("\"someString\" + \"\"", true)
      )
    ) { (expression, expected) =>
      NotBlankParameterValidator
        .isValid("dummy", Expression.spel(s"'$value'"), expression, None)
        .isValid shouldBe expected
    }
  }

  test("FixedValuesValidator") {
    val validator = FixedValuesValidator(List(FixedExpressionValue("'a'", "a"), FixedExpressionValue("'b'", "b")))
    forAll(
      Table(
        ("inputExpression", "isValid"),
        (null, true),
        ("", false),
        ("  ", false),
        ("\t", false),
        (" \n ", false),
        ("someString ", false),
        ("'someString' ", false),
        ("\"someString\" ", false),
        ("\"someString\" + \"\"", false),
        ("a", true),
        ("b", true),
        ("c", false),
        ("'a'", false),
        ("'b'", false),
        ("'c'", false),
      )
    ) { (expression, expected) =>
      validator.isValid("dummy", Expression.spel(s"'$value'"), expression, None).isValid shouldBe expected
    }
  }

  test("LiteralIntegerValidator") {
    val validator = LiteralIntegerValidator
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, true),
        ("'1'", "1", false),
        ("3.14", 3.14, false),
        ("1", 1, true),
      )
    ) { (expression, value, isValid) =>
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe isValid
    }
  }

  test("LiteralNumberValidator") {
    val validator = LiteralNumberValidator
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, true),
        ("'1'", "1", false),
        ("3.14", 3.14, true),
        ("1", 1, true),
        ("'ala'", "ala", false),
      )
    ) { (expression, value, isValid) =>
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe isValid
    }
  }

  test("MinimalNumberValidator") {
    val validator = MinimalNumberValidator(5)
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, true),
        ("'1'", "1", false),
        ("3.14", 3.14, false),
        ("1", 1, false),
        ("5", 5, true),
        ("6", 6, true),
        ("21.37", 21.37, true),
      )
    ) { (expression, value, isValid) =>
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe isValid
    }
  }

  test("MaximalNumberValidator") {
    val validator = MaximalNumberValidator(5)
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, true),
        ("'1'", "1", false),
        ("3.14", 3.14, true),
        ("1", 1, true),
        ("5", 5, true),
        ("6", 6, false),
        ("21.37", 21.37, false),
      )
    ) { (expression, value, isValid) =>
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe isValid
    }
  }

  test("LiteralRegExpParameterValidator") {
    val mailValidator = LiteralRegExpParameterValidator("^[^<>]+@nussknacker\\.io$", "", "")
    val alaValidator  = LiteralRegExpParameterValidator("^ala$", "", "")

    forAll(
      Table(
        ("inputValue", "validator", "isValid"),
        (null, mailValidator, true),
        ("''", mailValidator, false),
        ("", mailValidator, false),
        ("lcl@nussknacker.io", mailValidator, true),
        ("'lcl@nussknacker.io", mailValidator, true),
        ("lcl@nussknacker.io'", mailValidator, false),
        ("lcl@nussknacker.ios", mailValidator, false),
        ("lcl@nussknacker.ios", mailValidator, false),
        ("lcl@nussknacker.ios", LiteralParameterValidator.numberValidator, false),
        (0, LiteralParameterValidator.numberValidator, true),
        ("ala", alaValidator, true),
        ("kot'", alaValidator, false),
      )
    ) { (value, validator, expected) =>
      validator.isValid("dummy", Expression.spel(s"'$value'"), value, None).isValid shouldBe expected
    }
  }

}
