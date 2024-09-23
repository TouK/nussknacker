package pl.touk.nussknacker.engine.benchmarks.spel

import cats.data.Validated.{Invalid, Valid}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
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

import java.util.concurrent.TimeUnit

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class SpelBenchmarkSetup(expression: String, vars: Map[String, AnyRef]) {

  private val expressionConfig        = ModelDefinitionBuilder.emptyExpressionConfig
  private val globalVariablesPreparer = GlobalVariablesPreparer(expressionConfig)
  private val evaluator               = ExpressionEvaluator.unOptimizedEvaluator(globalVariablesPreparer)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    expressionConfig,
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

@State(Scope.Thread)
class SampleSpelBenchmark {

  private val setup = new SpelBenchmarkSetup("#input.substring(0, 3)", Map("input" -> "one"))

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def benchmark(): AnyRef = {
    setup.test()
  }

}
