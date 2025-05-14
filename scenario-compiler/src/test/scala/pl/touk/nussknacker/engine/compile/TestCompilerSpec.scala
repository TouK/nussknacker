package pl.touk.nussknacker.engine.compile

import cats.data.Validated
import cats.data.Validated.Valid
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.{Test, TestSourceInput}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.JsonTemplate
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

class TestCompilerSpec extends AnyFunSuite with Matchers with Inside {

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withSink("sink")
    .build

  test("initial test compiler test") {
    val scenario = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val test = Test(
      "someTest",
      inputs = Map("id1" -> List(TestSourceInput(Expression(JsonTemplate, """{"input": 1}""")))),
      mocks = Map.empty,
      assertions = Map()
    )

    val typing       = compileScenarioForTyping(scenario)
    val testCompiler = new TestCompiler()

    val testCompilationResult = testCompiler.compile(test, typing)

    inside(testCompilationResult) { case Valid(compiledTest) =>
      compiledTest.inputs.size shouldBe 1
      compiledTest.mocks shouldBe Map.empty
      compiledTest.assertions shouldBe Map.empty
    }
  }

  private def compileScenarioForTyping(scenario: CanonicalProcess): Map[String, NodeTypingInfo] = {
    val jobData: JobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

    val compilationResult = ProcessValidator
      .default(
        ModelDefinitionWithClasses(baseDefinition),
        new SimpleDictRegistry(Map.empty),
        CustomProcessValidatorLoader.emptyCustomProcessValidator,
      )
      .asInstanceOf[ProcessCompiler]
      .compile(scenario)

    compilationResult.result match {
      case Validated.Valid(_) => compilationResult.typing
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(s"Process compilation ended with errors: $errors")
    }
  }

}
