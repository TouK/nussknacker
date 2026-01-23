package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import jdk.internal.net.http.common.Log.errors
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo, ProcessCompiler, ProcessValidator}
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.Assertion
import pl.touk.nussknacker.engine.test.testcase.Assertion.PredicateAssertion
import pl.touk.nussknacker.engine.test.testcase.TestCase
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.PredicateAssertionCompilationError
import pl.touk.nussknacker.ui.process.test.testcase.AssertionValidationError.AssertionConfiguredForNotExistingNodesError
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.CompiledPredicateAssertion

import java.util.UUID

class AssertionsCompilerSpec extends AnyFunSuite with Matchers with Inside {

  import pl.touk.nussknacker.engine.util.Implicits._

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .build

  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(baseDefinition)

  private val assertionsCompiler: AssertionsCompiler = {
    val globalVariablesPreparer =
      GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
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
    val test = prepareTestCase(
      Map(
        NodeId("sink1") -> List(PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#contexts.size".spel))
      )
    )

    val testCompilationResult = compileScenarioWithAssertions(scenario, test)
    inside(testCompilationResult) { case Valid(compiledAssertions) =>
      compiledAssertions.assertions.size shouldBe 1
      compiledAssertions.assertions(NodeId("sink1")).size shouldBe 1
      val compiledAssertion =
        compiledAssertions.assertions(NodeId("sink1")).head.asInstanceOf[CompiledPredicateAssertion]
      compiledAssertion.operator shouldBe Assertion.AssertionOperator.Equals
      compiledAssertion.expectedExpression.original shouldBe "1"
      compiledAssertion.actualExpression.original shouldBe "#contexts.size"
    }
  }

  test("should produce errors for assertions on missing nodes") {
    val scenario = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .enricher("enricher1", "enricherOutput", "enricher1", "par1" -> "'abc'".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("sink1", "sink")
    val test = prepareTestCase(
      Map(
        NodeId("notExistingSink") -> List(
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#contexts.size".spel)
        )
      )
    )

    val testCompilationResult = compileScenarioWithAssertions(scenario, test)

    inside(testCompilationResult) { case Invalid(errors) =>
      errors.toList shouldBe List(
        AssertionConfiguredForNotExistingNodesError(
          NonEmptyList.one(NodeId("notExistingSink"))
        )
      )
    }
  }

  test("should produce errors for assertions with syntax errors") {
    val scenario = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .enricher("enricher1", "enricherOutput", "enricher1", "par1" -> "'abc'".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("sink1", "sink")
    val nodeId = NodeId("sink1")
    val assertionWithUnneededComma =
      PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#contexts.size,".spel)
    val assertionComparingUnrelatedTypes =
      PredicateAssertion(Assertion.AssertionOperator.Equals, "'string'".spel, "123".spel)
    val test = prepareTestCase(
      Map(
        nodeId -> List(
          assertionWithUnneededComma,
          assertionComparingUnrelatedTypes,
        )
      )
    )

    val testCompilationResult = compileScenarioWithAssertions(scenario, test)
    inside(testCompilationResult) { case Invalid(errors) =>
      errors.size shouldBe 2

      val assertionWithUnneededCommaError :: assertionComparingUnrelatedTypesError :: Nil = errors.toList
      assertionWithUnneededCommaError shouldBe PredicateAssertionCompilationError(
        NonEmptyList.one(
          ExpressionParserCompilationError(
            "Unexpected text",
            nodeId,
            Some(ParameterName(PredicateAssertionCompilationError.Field.Actual.entryName)),
            "#contexts.size,",
            Some(CoordinatesBasedTextRange(TextCoordinates(14, 0), TextCoordinates(15, 0)))
          )
        ),
        assertionWithUnneededComma,
        Some(PredicateAssertionCompilationError.Field.Actual),
        nodeId
      )

      assertionComparingUnrelatedTypesError shouldBe PredicateAssertionCompilationError(
        NonEmptyList.one(
          ExpressionParserCompilationError(
            "Bad expression type, expected: String(string), found: Integer(123)",
            nodeId,
            Some(ParameterName(PredicateAssertionCompilationError.Field.Actual.entryName)),
            "123",
            details = None
          )
        ),
        assertionComparingUnrelatedTypes,
        Some(PredicateAssertionCompilationError.Field.Actual),
        nodeId
      )
    }
  }

  private def compileScenarioWithAssertions(scenario: CanonicalProcess, test: TestCase) = {
    val typing  = compileScenarioForTyping(scenario)
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
      case Validated.Valid(_) => compilationResult.typing.mapValuesNow(nodeInfoToResult).toMap
      case Validated.Invalid(errors) =>
        throw new IllegalStateException(s"Process compilation ended with errors: $errors")
    }
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.localVariables,
    typingInfo.parameters.map(_.map(DefinitionsService.createUIParameter)),
    typingInfo.expressionsTypingInfo
  )

  private def prepareTestCase(assertions: Map[NodeId, List[Assertion]]): TestCase = {
    TestCase(
      id = UUID.randomUUID(),
      name = "dummy",
      inputs = "dummy",
      mocks = Map.empty,
      assertions = assertions
    )
  }

}
