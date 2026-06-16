package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

  test("MandatoryExpressionValidator") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", Some(null), false),
        ("  ", Some(null), false),
        ("\t", Some(null), false),
        (" \n ", Some(null), false),
        ("null", Some(null), true),
        ("#input.foo['bar']", None, true),
        ("true", Some(true), true),
        ("''", Some(""), true),
        ("'foo'", Some("foo"), true),
        ("1", Some(1), true),
        ("1 + 1", Some(2), true),
      )
    ) { (expression, value, expected) =>
      MandatoryExpressionValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value, None)
        .isValid shouldBe expected
    }
  }

  test("NonNullParameterValidator") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", Some(null), false),
        ("  ", Some(null), false),
        ("\t", Some(null), false),
        (" \n ", Some(null), false),
        ("null", Some(null), false),
        ("#input.foo['bar']", None, true),
        ("true", Some(true), true),
        ("''", Some(""), true),
        ("'foo'", Some("foo"), true),
        ("1", Some(1), true),
        ("1 + 1", Some(2), true),
      )
    ) { (expression, value, expected) =>
      NotNullValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value, None)
        .isValid shouldBe expected
    }
  }

  test("NotBlankValidator") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", Some(null), true),
        ("", Some(null), true),
        ("  ", Some(null), true),
        ("\t", Some(null), true),
        (" \n ", Some(null), true),
        ("#input.foo['bar']", None, true),
        ("' ' ", Some(" "), false),
        ("' ' + ' '", Some("  "), false),
        ("'\t'", Some("\t"), false),
        ("'\n'", Some("\n"), false),
        ("'someString' ", Some("someString"), true),
        ("\"someString\" ", Some("someString"), true),
        ("\"someString\" + \"\"", Some("someString"), true),
      )
    ) { (expression, value, expected) =>
      NotBlankValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value, None)
        .isValid shouldBe expected
    }
  }

  test("FixedValuesExpressionValidator") {
    val validator = FixedValuesExpressionValidator(
      List(
        FixedExpressionValue("'a'", "a"),
        FixedExpressionValue("'b'", "b"),
        FixedExpressionValue("'\n'", "New line"),
      )
    )
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", Some(null), false),
        ("", Some(null), true),
        ("  ", Some(null), true),
        ("\t", Some(null), true),
        ("\n''", Some(""), false),
        ("#input.foo['bar']", None, false),
        ("'someString' ", Some("someString"), false),
        ("\"someString\" ", Some("someString"), false),
        ("'a'", Some("a"), true),
        ("'b'", Some("b"), true),
        ("'c'", Some("c"), false),
      )
    ) { (expression, value, expected) =>
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("FixedValuesExpressionValidator for non-evaluable expressions") {
    val validator = FixedValuesExpressionValidator(
      List(
        FixedExpressionValue("#AGG.first", "First"),
        FixedExpressionValue("#AGG.last", "Last"),
      )
    )
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", Some(null), false),
        ("", Some(null), true),
        ("  ", Some(null), true),
        ("\t", Some(null), true),
        ("\n''", Some(""), false),
        ("#input.foo['bar']", None, false),
        ("'someString' ", Some("someString"), false),
        ("\"someString\" ", Some("someString"), false),
        ("#AGG.first", None, true),
        ("#AGG.middle", None, false),
        ("'c'", Some("c"), false),
      )
    ) { (expression, value, expected) =>
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("LiteralIntegerExpressionValidator") {
    val validator = LiteralIntegerExpressionValidator
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", null, true),
        ("'1'", "1", false),
        ("3.14", 3.14, false),
        ("1", 1, true),
        ("'ala'", "ala", false),
      )
    ) { (expression, value, isValid) =>
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), Some(value), None).isValid shouldBe isValid
    }
  }

  test("CompileTimeEvaluableValueValidator") {
    val validator = CompileTimeEvaluableValueValidator
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("", Some(null), true),
        ("null", Some(null), true),
        ("2+2", Some(4), true),
        ("'foo' + 'bar'", Some("foobar"), true),
        ("#input", None, false),
      )
    ) { (expression, value, isValid) =>
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), value, None).isValid shouldBe isValid
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
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), Some(value), None).isValid shouldBe isValid
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
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), Some(value), None).isValid shouldBe isValid
    }
  }

  test("RegExpValidator") {
    val mailValidator = RegExpValidator("^[^<>]+@nussknacker\\.io$", "", "")
    val alaValidator  = RegExpValidator("^ala$", "", "")

    forAll(
      Table(
        ("expression", "value", "validator", "isValid"),
        ("", Some(null), mailValidator, true),
        ("null", Some(null), mailValidator, true),
        ("''", Some(""), mailValidator, false),
        ("'lcl@nussknacker.io'", Some("lcl@nussknacker.io"), mailValidator, true),
        ("\"lcl@nussknacker.io\"", Some("lcl@nussknacker.io"), mailValidator, true),
        ("'lcl@nussknacker.ios'", Some("lcl@nussknacker.ios"), mailValidator, false),
        ("'ala'", Some("ala"), alaValidator, true),
        ("'kot'", Some("kot"), alaValidator, false),
      )
    ) { (expression, value, validator, expected) =>
      validator.isValid(ParameterName("dummy"), Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("NotNullValidator runtime validation") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, false),
        ("''", "", true),
        ("' '", " ", true),
        ("'foo'", "foo", true),
        ("1", 1, true),
        ("{}", java.util.List.of(), true),
      )
    ) { (expression, value, expected) =>
      NotNullValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

  test("NotEmptyCollectionValidator runtime validation") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, true),
        ("{}", java.util.List.of(), false),
        ("{1}", java.util.List.of(1), true),
        ("{:}", java.util.Map.of(), false),
        ("{foo: 1}", java.util.Map.of("foo", 1), true),
        ("'foo'", "foo", true),
      )
    ) { (expression, value, expected) =>
      NotEmptyCollectionValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

  test("NotEmptyCollectionValidator compile-time validation") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("#input", None, true),
        ("null", Some(null), true),
        ("{}", Some(java.util.List.of()), false),
        ("{1}", Some(java.util.List.of(1)), true),
        ("{:}", Some(java.util.Map.of()), false),
        ("{foo: 1}", Some(java.util.Map.of("foo", 1)), true),
        ("'foo'", Some("foo"), true),
      )
    ) { (expression, value, expected) =>
      NotEmptyCollectionValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value, None)
        .isValid shouldBe expected
    }
  }

  test("NotBlankValidator runtime validation") {
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, true),
        ("''", "", false),
        ("' '", " ", false),
        ("'\t'", "\t", false),
        ("'foo'", "foo", true),
        ("1", 1, true),
      )
    ) { (expression, value, expected) =>
      NotBlankValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

  test("RegExpValidator runtime validation") {
    val mailValidator = RegExpValidator("^[^<>]+@nussknacker\\.io$", "", "")
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, true),
        ("'lcl@nussknacker.io'", "lcl@nussknacker.io", true),
        ("'lcl@nussknacker.ios'", "lcl@nussknacker.ios", false),
        ("''", "", false),
        ("1", 1, false),
      )
    ) { (expression, value, expected) =>
      mailValidator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

  test("MinimalNumberValidator runtime validation") {
    val validator = MinimalNumberValidator(5)
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, true),
        ("1", 1, false),
        ("5", 5, true),
        ("6", 6, true),
        ("21.37", 21.37, true),
        ("'foo'", "foo", false),
      )
    ) { (expression, value, expected) =>
      validator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

  test("MaximalNumberValidator runtime validation") {
    val validator = MaximalNumberValidator(5)
    forAll(
      Table(
        ("expression", "value", "isValid"),
        ("null", null, true),
        ("1", 1, true),
        ("5", 5, true),
        ("6", 6, false),
        ("21.37", 21.37, false),
        ("'foo'", "foo", false),
      )
    ) { (expression, value, expected) =>
      validator
        .isValid(ParameterName("dummy"), Expression.spel(expression), value)
        .isValid shouldBe expected
    }
  }

}
