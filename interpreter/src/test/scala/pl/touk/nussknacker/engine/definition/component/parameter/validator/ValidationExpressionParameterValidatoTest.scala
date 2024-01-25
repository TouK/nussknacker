package pl.touk.nussknacker.engine.definition.component.parameter.validator

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression}
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.jdk.CollectionConverters._

class ValidationExpressionParameterValidatorTest extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

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

private class TestSpelExpression(expression: String) extends CompiledExpression {

  override def language: String = "spel"

  override def original: String = expression

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
    val context = new StandardEvaluationContext()
    ctx.variables.foreach { case (param, value) => context.setVariable(param, value) }
    new SpelExpressionParser().parseRaw(expression).getValue(context).asInstanceOf[T]
  }

}
