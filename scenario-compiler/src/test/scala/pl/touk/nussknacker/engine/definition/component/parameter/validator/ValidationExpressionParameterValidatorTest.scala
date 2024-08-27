package pl.touk.nussknacker.engine.definition.component.parameter.validator

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{Context, MetaData, NodeId, StreamMetaData}
import pl.touk.nussknacker.engine.definition.component.parameter.validator.TestSpelExpression.expressionConfig
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.jdk.CollectionConverters._

class ValidationExpressionParameterValidatorTest extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

  test("ValidationExpressionParameterValidator") {
    forAll(
      Table(
        ("validationExpression", "paramName", "inputExpression", "value", "isValid"),
        ("#value > 10", "param", "", Some(null), true),
        ("#value > 10", "param", "#input.foo", None, false),
        ("#value > 10", "param", "-14", Some(-14), false),
        (
          "#value.toLowerCase() == \"left\" || #value.toLowerCase() == \"right\"",
          "param",
          "'lEfT'",
          Some("lEfT"),
          true
        ),
        ("#value.toLowerCase() == \"left\" || #value.toLowerCase() == \"right\"", "param", "'up'", Some("up"), false),
        ("#value.size() == 2 && #value[0] == 'foo'", "list", "{'foo', 'bar'}", Some(List("foo", "bar").asJava), true),
        ("#ADD.add(#value, 3) > 10", "param", "9", Some(9), true),
        ("#ADD.add(#value, 3) > 10", "param", "7", Some(7), false),
        ("#value == #meta.processName.toLowerCase", "param", "fooprocess", Some("fooprocess"), true),
        ("#value == #meta.processName.toLowerCase", "param", "bar_baz", Some("bar_baz"), false),
      )
    ) { (validationExpression, paramName, inputExpression, value, isValid) =>
      ValidationExpressionParameterValidator(
        new TestSpelExpression(validationExpression),
        None,
        ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(expressionConfig)),
        MetaData("FooProcess", StreamMetaData())
      )
        .isValid(ParameterName(paramName), Expression.spel(inputExpression), value, None)(nodeId)
        .isValid shouldBe isValid
    }
  }

}

private class TestSpelExpression(expression: String) extends CompiledExpression {

  override val language: Language = Language.Spel

  override def original: String = expression

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
    val evaluationContext = EvaluationContextPreparer
      .default(getClass.getClassLoader, expressionConfig)
      .prepareEvaluationContext(ctx, globals)

    new SpelExpressionParser().parseRaw(expression).getValue(evaluationContext).asInstanceOf[T]
  }

}

object TestSpelExpression {

  object Add {
    def add(a: java.lang.Integer, b: java.lang.Integer): java.lang.Integer = a + b
  }

  val expressionConfig: ExpressionConfigDefinition =
    ModelDefinitionBuilder.empty
      .withGlobalVariable("ADD", Add)
      .build
      .expressionConfig

}
