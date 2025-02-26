package pl.touk.nussknacker.engine.benchmarks.suggester

import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, ClassDefinitionTestUtils}
import pl.touk.nussknacker.engine.dict.{SimpleDictQueryService, SimpleDictRegistry}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.CaretPosition2d
import pl.touk.nussknacker.ui.suggester.ExpressionSuggester

import java.time.{Duration, LocalDateTime}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{Duration => ScalaDuration}

/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class ExpressionSuggesterBenchmarkSetup() {
  private val classDefinitionExtractor = ClassDefinitionTestUtils.DefaultExtractor

  private val dictRegistry = new SimpleDictRegistry(
    Map(
      "dictFoo" -> EmbeddedDictDefinition(Map("one" -> "One", "two" -> "Two")),
      "dictBar" -> EmbeddedDictDefinition(Map("sentence-with-spaces-and-dots" -> "Sentence with spaces and . dots")),
    )
  )

  private val dictServices = UiDictServices(dictRegistry, new SimpleDictQueryService(dictRegistry, 10))

  private val clazzDefinitions: ClassDefinitionSet = ClassDefinitionSet(
    Set(
      classDefinitionExtractor.extract(classOf[Foo]),
      classDefinitionExtractor.extract(classOf[Bar]),
      classDefinitionExtractor.extract(classOf[String]),
      classDefinitionExtractor.extract(classOf[LocalDateTime]),
      classDefinitionExtractor.extract(classOf[Duration]),
    )
  )

  private val expressionSuggester = new ExpressionSuggester(
    ModelDefinitionBuilder.emptyExpressionConfig,
    clazzDefinitions,
    dictServices,
    getClass.getClassLoader,
    List.empty
  )

  private val variables: Map[String, TypingResult] = Map(
    "foo"       -> Typed[Foo],
    "bar"       -> Typed[Bar],
    "stringVar" -> Typed[String],
    "intVar"    -> Typed[String],
  ) ++ (1 to 30).map(i => s"foo$i" -> Typed[Foo]) ++ (1 to 40).map(i => s"bar$i" -> Typed[Bar])

  def test(expression: String, position: Int): AnyRef = {
    Await.result(
      expressionSuggester.expressionSuggestions(
        Expression(Language.Spel, expression),
        CaretPosition2d(0, position),
        variables
      )(ExecutionContext.global),
      ScalaDuration("10s")
    )
  }

}

class Foo {
  def int(): Int       = 42
  def string(): String = "nussknacker"
  def double(): Double = 3.1415
  def bar(): Bar       = new Bar()
}

class Bar {
  def foo(): Foo = new Foo()
}
