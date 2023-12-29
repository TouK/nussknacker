package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.graph.expression.{Expression, FixedExpressionValue}
import pl.touk.nussknacker.engine.api.expression.{Expression => ApiExpression}

import scala.jdk.CollectionConverters._

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

  test("MandatoryParameterValidator") {
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
      MandatoryParameterValidator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
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
      NotNullParameterValidator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("NotBlankParameterValidator") {
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
      NotBlankParameterValidator
        .isValid("dummy", Expression.spel(expression), value, None)
        .isValid shouldBe expected
    }
  }

  test("FixedValuesValidator") {
    val validator = FixedValuesValidator(
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
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("FixedValuesValidator for non-evaluable expressions") {
    val validator = FixedValuesValidator(
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
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
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
        ("'ala'", "ala", false),
      )
    ) { (expression, value, isValid) =>
      validator.isValid("dummy", Expression.spel(expression), Some(value), None).isValid shouldBe isValid
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
      validator.isValid("dummy", Expression.spel(expression), Some(value), None).isValid shouldBe isValid
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
      validator.isValid("dummy", Expression.spel(expression), Some(value), None).isValid shouldBe isValid
    }
  }

  test("RegExpParameterValidator") {
    val mailValidator = RegExpParameterValidator("^[^<>]+@nussknacker\\.io$", "", "")
    val alaValidator  = RegExpParameterValidator("^ala$", "", "")

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
      validator.isValid("dummy", Expression.spel(expression), value, None).isValid shouldBe expected
    }
  }

  test("ValidationExpressionParameterValidator") {
    forAll(
      Table(
        ("validationExpression", "paramName", "inputExpression", "value", "isValid"),
        ("#value > 10", "param", "", Some(null), true),
        ("#value > 10", "param", "#input.foo", None, true),
        ("#value > 10", "param", "-14", Some(-14), false),
        (
          "#value.toLowerCase() == \"left\" || #value.toLowerCase() == \"right\"",
          "param",
          "'lEfT'",
          Some("lEfT"),
          true
        ),
        ("#value.toLowerCase() == \"left\" || #value.toLowerCase() == \"right\"", "param", "'up'", Some("up"), false),
        ("#value", "param", "'up'", Some("up"), false),
        ("#value", "param", "'up'", Some("up"), false),
        ("#value.size() == 2 && #value[0] == 'foo'", "list", "{'foo', 'bar'}", Some(List("foo", "bar").asJava), true)
      )
    ) { (validationExpression, paramName, inputExpression, value, isValid) =>
      ValidationExpressionParameterValidator(new TestSpelExpression(validationExpression), None)
        .isValid(paramName, Expression.spel(inputExpression), value, None)(nodeId)
        .isValid shouldBe isValid
    }
  }

}

private class TestSpelExpression(expression: String) extends ApiExpression {

  override def language: String = "spel"

  override def original: String = expression

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
    val context = new StandardEvaluationContext()
    ctx.variables.foreach { case (param, value) => context.setVariable(param, value) }
    new SpelExpressionParser().parseRaw(expression).getValue(context).asInstanceOf[T]
  }

}
