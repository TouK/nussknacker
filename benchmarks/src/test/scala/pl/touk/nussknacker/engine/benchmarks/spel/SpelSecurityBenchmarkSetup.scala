package pl.touk.nussknacker.engine.benchmarks.spel

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class SpelSecurityBenchmarkSetup(expression: String, vars: Map[String, AnyRef]) {

  private val expressionDefinition    = ModelDefinitionBuilder.emptyExpressionConfig
  private val globalVariablesPreparer = GlobalVariablesPreparer(expressionDefinition)
  private val evaluator               = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    expressionDefinition,
    classDefinitionSet = ClassDefinitionTestUtils.createDefinitionForDefaultAdditionalClasses,
    evaluator
  )

  private val validationContext = ValidationContext(vars.mapValuesNow(Typed.fromInstance), Map.empty)

  private val compiledExpression =
    expressionCompiler.compile(Expression.spel(expression), None, validationContext, Unknown)(NodeId("")) match {
      case Valid(a)   => a.expression
      case Invalid(e) => throw new IllegalArgumentException(s"Failed to parse: $e")
    }

  private val ctx = Context("id", vars, None)

  def test(): AnyRef = {
    compiledExpression.evaluate[AnyRef](ctx, Map.empty)
  }

}
