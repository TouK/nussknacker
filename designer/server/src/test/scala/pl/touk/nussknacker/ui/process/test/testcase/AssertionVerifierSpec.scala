package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.{FailedAssertion, ResultContext, SuccessfulAssertion}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

import java.time.Instant

class AssertionVerifierSpec extends AnyFunSuite with Matchers {

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .build

  private val modelDefinitionWithClasses = ModelDefinitionWithClasses(baseDefinition)

  private val expressionCompiler = ExpressionCompiler.withOptimization(
    getClass.getClassLoader,
    new SimpleDictRegistry(Map.empty),
    modelDefinitionWithClasses.modelDefinition.expressionConfig,
    modelDefinitionWithClasses.classDefinitions,
    ExpressionEvaluator.unOptimizedEvaluator(
      GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    )
  )

  private val testCompiler = new TestCaseCompiler(expressionCompiler)

  test("should run assertions on test nodes results") {
    val scenarioTyping: Map[String, NodeTypingData] = Map(
      "someNode" -> NodeTypingData(Map("someVariable" -> Typed.fromInstance("bar")), None, Map.empty, None)
    )

    val testCase = TestCase(
      "dummy",
      "dummy",
      Map.empty,
      Map(
        NodeId("someNode") -> List(
          Assertion("#TESTS.assertEquals('valid', #contexts[0].someVariable)"),
          Assertion("#TESTS.assertEquals('valid', #contexts[1].someVariable)"),
        )
      )
    )

    val compiledTestCase = testCompiler
      .compile(testCase, scenarioTyping)
      .fold(errors => throw new IllegalStateException(s"Test compilation errors: $errors"), identity)
    val verifier = new AssertionVerifierImpl()

    val results = verifier.verify(
      compiledTestCase,
      Map(
        NodeId("someNode") -> List(
          ResultContext[Any](ContextId.dummy, Instant.now(), Map("someVariable" -> "valid")),
          ResultContext[Any](ContextId.dummy, Instant.now(), Map("someVariable" -> "invalid")),
        )
      )
    )

    results.toList shouldBe List(
      NodeId("someNode") -> List(SuccessfulAssertion, FailedAssertion("Expected: [valid] but found [invalid]"))
    )
  }

}
