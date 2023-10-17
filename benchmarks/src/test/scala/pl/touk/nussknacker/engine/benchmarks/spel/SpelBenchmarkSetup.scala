package pl.touk.nussknacker.engine.benchmarks.spel

import cats.data.Validated.{Invalid, Valid}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{Context, NodeId, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.util.concurrent.TimeUnit

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class SpelBenchmarkSetup(expression: String, vars: Map[String, AnyRef]) {

  private val expressionDefinition = ExpressionDefinition(
    globalVariables = Map(),
    globalImports = Nil,
    additionalClasses = List(),
    languages = LanguageConfiguration.default,
    optimizeCompilation = true,
    strictTypeChecking = true,
    dictionaries = Map.empty,
    hideMetaVariable = false,
    strictMethodsChecking = true,
    staticMethodInvocationsChecking = true,
    methodExecutionForUnknownAllowed = false,
    dynamicPropertyAccessAllowed = false,
    spelExpressionExcludeList = SpelExpressionExcludeList.default,
    customConversionsProviders = List.empty
  )

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    expressionDefinition,
    typeDefinitionSet = TypeDefinitionSet.forDefaultAdditionalClasses
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
