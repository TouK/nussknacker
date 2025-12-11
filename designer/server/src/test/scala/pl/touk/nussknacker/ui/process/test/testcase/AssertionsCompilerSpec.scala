package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.TestConfigurationRefersToNotExistingNode
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo, ProcessCompiler, ProcessValidator}
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ScenarioCompilationDependencies}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.ui.definition.DefinitionsService

import java.util.UUID

class AssertionsCompilerSpec extends AnyFunSuite with Matchers with Inside {

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .build
  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(baseDefinition)

  private val assertionsCompiler: AssertionsCompiler = {
    val globalVariablesPreparer = GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    val expressionCompiler = ExpressionCompiler.withOptimization(
      getClass.getClassLoader,
      new SimpleDictRegistry(Map.empty),
      modelDefinitionWithClasses.modelDefinition.expressionConfig,
      modelDefinitionWithClasses.classDefinitions,
      ExpressionEvaluator.unOptimizedEvaluator(
        globalVariablesPreparer
      )
    )
    new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
  }

  test("should compile valid assertions for scenario") {
    val scenario = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .enricher("enricher1", "enricherOutput", "enricher1", "par1" -> "'abc'".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("sink1", "sink")

    val test = TestCase(
      UUID.randomUUID(),
      "someTest",
      inputs = "",
      mocks = Map.empty,
      assertions = Map(NodeId("sink1") -> List(Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel)))
    )

    val testCompilationResult = compileScenarioWithAssertions(scenario, test)
    inside(testCompilationResult) { case Valid(compiledAssertions) =>
      compiledAssertions.assertions.size shouldBe 1
      compiledAssertions.assertions(NodeId("sink1")).size shouldBe 1
      compiledAssertions.assertions(NodeId("sink1")).head.expression.original shouldBe "#TESTS.assertEquals(#contexts.size, 1)"
    }
  }

  test("should produce errors for assertions on missing nodes") {
    val scenario = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .enricher("enricher1", "enricherOutput", "enricher1", "par1" -> "'abc'".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("sink1", "sink")
    val test = TestCase(
      UUID.randomUUID(),
      "someTest",
      inputs = "",
      mocks = Map.empty,
      assertions = Map(NodeId("notExistingSink") -> List(Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel)))
    )

    val testCompilationResult = compileScenarioWithAssertions(scenario, test)

    inside(testCompilationResult) { case Invalid(errors) =>
      errors.toList shouldBe List(TestConfigurationRefersToNotExistingNode(
        NodeId("notExistingSink"),
        test.name,
        ProcessCompilationError.Assertion
      ))
    }
  }

  private def compileScenarioWithAssertions(scenario: CanonicalProcess, test: TestCase) = {
    val typing = compileScenarioForTyping(scenario)
    val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)

    val assertionCompilationResult = assertionsCompiler.compile(
      test,
      typing,
      jobData
    )
    assertionCompilationResult
  }

  private def compileScenarioForTyping(scenario: CanonicalProcess): Map[String, NodeTypingData] = {
    val jobData: JobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

    val compilationResult = ProcessValidator
      .default(
        modelDefinitionWithClasses,
        new SimpleDictRegistry(Map.empty),
        CustomProcessValidatorLoader.emptyCustomProcessValidator,
      )
      .asInstanceOf[ProcessCompiler]
      .compile(scenario)

    compilationResult.result match {
      case Validated.Valid(_) => compilationResult.typing.view.mapValues(nodeInfoToResult).toMap
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(s"Process compilation ended with errors: $errors")
    }
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.localVariables,
    typingInfo.parameters.map(_.map(DefinitionsService.createUIParameter)),
    typingInfo.expressionsTypingInfo
  )

}
