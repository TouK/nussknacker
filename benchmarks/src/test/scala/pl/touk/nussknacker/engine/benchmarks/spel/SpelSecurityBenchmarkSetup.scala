package pl.touk.nussknacker.engine.benchmarks.spel

import cats.data.Validated.{Invalid, Valid}
import org.openjdk.jmh.annotations._
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{Context, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.api.graph.expression.Expression

import java.util.concurrent.TimeUnit

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class SpelSecurityBenchmarkSetup(expression: String, vars: Map[String, AnyRef]) {

  private val expressionDefinition = ExpressionDefinition(globalVariables = Map(), globalImports = Nil, additionalClasses = List(),
    languages = LanguageConfiguration.default, optimizeCompilation = true, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false,
    strictMethodsChecking = true, staticMethodInvocationsChecking = false, methodExecutionForUnknownAllowed = true, dynamicPropertyAccessAllowed = false,
    SpelExpressionExcludeList.default)

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


