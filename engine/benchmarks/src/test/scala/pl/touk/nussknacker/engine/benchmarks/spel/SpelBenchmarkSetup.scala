package pl.touk.nussknacker.engine.benchmarks.spel

import java.util.concurrent.TimeUnit
import cats.data.Validated.{Invalid, Valid}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.expression.Expression

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class SpelBenchmarkSetup(expression: String, vars: Map[String, AnyRef]) {

  private val expressionDefinition = ExpressionDefinition(globalVariables = Map(), globalImports = Nil, additionalClasses = List(),
    languages = LanguageConfiguration.default, optimizeCompilation = true, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false,
    strictMethodsChecking = true, staticMethodInvocationsChecking = false, disableMethodExecutionForUnknown = false, dynamicPropertyAccessAllowed = false)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader, new SimpleDictRegistry(Map.empty), expressionDefinition, settings = ClassExtractionSettings.Default, typeDefinitionSet = TypeDefinitionSet.empty)

  private val validationContext = ValidationContext(vars.mapValues(Typed.fromInstance), Map.empty)

  private val compiledExpression = expressionCompiler.compile(Expression(language = "spel", expression = expression),
    None, validationContext, Unknown)(NodeId("")) match {
    case Valid(a) => a.expression
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

class ForTest(x: Integer, y: Integer) {
  def test(): String = {
    Math.floorMod(x,y).toString
  }

}

@State(Scope.Thread)
class SampleSpelBenchmarkForTest {

  private val setup = new ForTest(10000,20000)


  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def benchmark(): AnyRef = {
    setup.test()
  }
}

