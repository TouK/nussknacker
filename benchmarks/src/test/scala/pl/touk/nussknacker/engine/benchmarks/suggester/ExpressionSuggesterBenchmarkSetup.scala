package pl.touk.nussknacker.engine.benchmarks.suggester

import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.dict.{SimpleDictQueryService, SimpleDictRegistry}
import pl.touk.nussknacker.ui.suggester.{CaretPosition2d, ExpressionSuggester}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.types.EspTypeUtils
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression

import java.time.{Duration, LocalDateTime}
import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.concurrent.{Await, ExecutionContext}
/* This is helper class for testing SpEL expressions, see SampleSpelBenchmark for usage */
class ExpressionSuggesterBenchmarkSetup() {

  implicit val classExtractionSettings: ClassExtractionSettings = ClassExtractionSettings.Default

  private val dictRegistry = new SimpleDictRegistry(
    Map(
      "dictFoo" -> EmbeddedDictDefinition(Map("one" -> "One", "two" -> "Two")),
      "dictBar" -> EmbeddedDictDefinition(Map("sentence-with-spaces-and-dots" -> "Sentence with spaces and . dots")),
    )
  )
  private val dictServices = UiDictServices(dictRegistry, new SimpleDictQueryService(dictRegistry, 10))

  private val clazzDefinitions: TypeDefinitionSet = TypeDefinitionSet(
    Set(
      EspTypeUtils.clazzDefinition(classOf[Foo]),
      EspTypeUtils.clazzDefinition(classOf[Bar]),
      EspTypeUtils.clazzDefinition(classOf[String]),
      EspTypeUtils.clazzDefinition(classOf[LocalDateTime]),
      EspTypeUtils.clazzDefinition(classOf[Duration]),
    )
  )

  private val expressionSuggester = new ExpressionSuggester(
    ProcessDefinitionBuilder.empty.expressionConfig.copy(staticMethodInvocationsChecking = true),
    clazzDefinitions,
    dictServices,
    getClass.getClassLoader
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
        Expression("spel", expression),
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
