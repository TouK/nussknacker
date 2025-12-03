package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData, NodeId, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.{FailedAssertion, ResultContext, SuccessfulAssertion}
import pl.touk.nussknacker.engine.util.functions.conversion
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

import java.time.Instant

//todo: try to compare to arrays/lists
//todo: try to use some global in assertion like CONV.toAny()
class AssertionVerifierSpec extends AnyFunSuite with Matchers {

  private val baseDefinition = ModelDefinitionBuilder.empty
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withService("enricher1", Some(Typed[String]), Parameter[String](ParameterName("par1")))
    .withSink("sink")
    .withGlobalVariable("CONV", conversion)
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

  private val testCaseGlobalVariablesPreparer = GlobalVariablesPreparer(modelDefinitionWithClasses.modelDefinition.expressionConfig)
  private val testCompiler = new TestCaseCompiler(expressionCompiler, testCaseGlobalVariablesPreparer)
  private val verifier = new AssertionVerifierImpl(testCaseGlobalVariablesPreparer)

  private val scenarioTyping: Map[String, NodeTypingData] = Map(
    "someNode" -> NodeTypingData(
      Map(
        "someVariable" -> Typed.fromInstance("bar")

      ), None, Map.empty, None)
  )

  test("should run assertions on test nodes results") {
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

    val nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]] = Map(
      NodeId("someNode") -> List(
        ResultContext[Any](ContextId.dummy, Instant.now(), Map("someVariable" -> "valid")),
        ResultContext[Any](ContextId.dummy, Instant.now(), Map("someVariable" -> "invalid")),
      )
    )

    val results = verifyForTestCase(
      testCase,
      nodesResultsAfterTestRun
    )

    results.toList shouldBe List(
      NodeId("someNode") -> List(
        SuccessfulAssertion,
        FailedAssertion("Expected: [valid] but found [invalid]"))
    )
  }

  test("edge cases") {
    val testCase = TestCase(
      "dummy",
      "dummy",
      Map.empty,
      Map(
        NodeId("someNode") -> List(
          Assertion("#TESTS.assertEquals('valid', 'valid')"),
          Assertion("#TESTS.assertEquals({}, {})"),
          Assertion("#TESTS.assertEquals({:}, {:})"),
          Assertion("#TESTS.assertEquals(#CONV.toAny('abc'), 'abc')"),
        )
      )
    )

    val nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]] = Map()

    val results = verifyForTestCase(
      testCase,
      nodesResultsAfterTestRun
    )

    results.toList shouldBe List(
      NodeId("someNode") -> List(
        SuccessfulAssertion,
        SuccessfulAssertion,
        SuccessfulAssertion,
        SuccessfulAssertion,
      )
    )
  }

  private def verifyForTestCase(testCase: TestCase, nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]]) = {
    val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)
    val compiledTestCase = testCompiler
      .compile(testCase, scenarioTyping, jobData)
      .fold(errors => throw new IllegalStateException(s"Test compilation errors: $errors"), identity)
    verifier.verify(
      compiledTestCase,
      nodesResultsAfterTestRun,
      jobData
    )
  }

}
