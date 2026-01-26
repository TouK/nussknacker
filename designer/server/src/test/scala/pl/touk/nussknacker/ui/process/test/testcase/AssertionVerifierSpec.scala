package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData, NodeId, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, ExpressionAssertion, PredicateAssertion}
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.ResultContext
import pl.touk.nussknacker.engine.util.functions.conversion
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

import java.time.Instant
import java.util
import java.util.UUID

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
      GlobalVariablesPreparer(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    )
  )

  private val globalVariablesPreparer = GlobalVariablesPreparer(
    modelDefinitionWithClasses.modelDefinition.expressionConfig
  )

  private val assertionsCompiler = new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
  private val assertionVerifier  = new AssertionVerifier(globalVariablesPreparer)

  private val nodeId = NodeId("someNode")

  private val scenarioTyping: Map[String, NodeTypingData] = Map(
    nodeId.id -> NodeTypingData(
      variableTypes = Map(
        "someVariable"   -> Typed.fromInstance("bar"),
        "someJavaList"   -> Typed.fromInstance(new util.ArrayList[String]()),
        "someArray"      -> Typed.fromInstance(new Array[String](1)),
        "someBigDecimal" -> Typed[java.math.BigDecimal],
      ),
      parameters = None,
      typingInfo = Map.empty
    )
  )

  test("should run assertions on test nodes results and return assertion result for each assertion") {
    val testCase = prepareTestCase(
      List(
        ExpressionAssertion("#TESTS.assertEquals('valid', #contexts[0].someVariable)".spel),
        ExpressionAssertion("#TESTS.assertEquals('valid', #contexts[1].someVariable)".spel),
        PredicateAssertion(AssertionOperator.Equals, "'valid'".spel, "#contexts[0].someVariable".spel),
        PredicateAssertion(AssertionOperator.Equals, "'valid'".spel, "#contexts[1].someVariable".spel),
        PredicateAssertion(AssertionOperator.NotEquals, "'valid'".spel, "#contexts[0].someVariable".spel),
        PredicateAssertion(AssertionOperator.NotEquals, "'valid'".spel, "#contexts[1].someVariable".spel),
      )
    )
    val nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]] = prepareNodeResults(
      List(
        Map("someVariable" -> "valid"),
        Map("someVariable" -> "invalid"),
      )
    )

    val results = verifyForTestCase(testCase, nodesResultsAfterTestRun)

    results shouldBe List(
      SuccessfulAssertion,
      FailedAssertion("Expected: ['valid'] but found ['invalid']"),
      SuccessfulAssertion,
      FailedAssertion("Expected: ['valid'] but found ['invalid']"),
      FailedAssertion("Expected value different from: ['valid']"),
      SuccessfulAssertion,
    )
  }

  test("should properly compare various types used in SpEL") {
    forAll(
      Table(
        ("expected expression", "actual expression", "expected assertion result"),
        ("'valid'", "'valid'", SuccessfulAssertion),
        ("{}", "{}", SuccessfulAssertion),
        ("{:}", "{:}", SuccessfulAssertion),
        ("#CONV.toAny('abc')", "'abc'", SuccessfulAssertion),
        ("{'foo'}", "#contexts[0].someJavaList", SuccessfulAssertion),
        ("{'foo'}", "{'foo'}", SuccessfulAssertion),
        ("{'foo': 'bar'}", "{'foo': 'bar'}", SuccessfulAssertion),
        ("1", "1L", SuccessfulAssertion),
        ("null", "null", SuccessfulAssertion),
        ("{'foo'}", "{}", FailedAssertion("Expected: [{'foo'}] but found [{}]")),
        ("'1,2,3'.split(',')", "'1,2,3'.split(',')", SuccessfulAssertion), // comparing arrays
        (
          "'1,2'.split(',')",
          "'1,2,3'.split(',')",
          FailedAssertion("Expected: [{'1', '2'}] but found [{'1', '2', '3'}]")
        ),
        ("{'a': 1}", "{:}", FailedAssertion("Expected: [{'a': 1}] but found [{:}]")),
      )
    ) { (expectedExpression, actualExpression, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          ExpressionAssertion(s"#TESTS.assertEquals($expectedExpression, $actualExpression)".spel),
          PredicateAssertion(AssertionOperator.Equals, expectedExpression.spel, actualExpression.spel),
        )
      )
      val nodesResultsAfterTestRun = prepareNodeResults(
        List(
          Map("someJavaList" -> java.util.List.of("foo"))
        )
      )

      val results = verifyForTestCase(testCase, nodesResultsAfterTestRun)

      results shouldBe List(expectedResult, expectedResult)
    }
  }

  test("should apply SpEL conversions") {
    forAll(
      Table(
        ("expected expression", "actual expression", "expected assertion result"),
        ("1", "#contexts[0].someBigDecimal", SuccessfulAssertion),
        ("1L", "#contexts[0].someBigDecimal", SuccessfulAssertion),
        ("1.0", "#contexts[0].someBigDecimal", SuccessfulAssertion),
        ("1.0f", "#contexts[0].someBigDecimal", SuccessfulAssertion),
        ("1", "T(java.math.BigDecimal).ONE", SuccessfulAssertion),
        ("{a: 1}", """#CONV.toJson('{"a": 1}')""", SuccessfulAssertion),
        ("{a: 1}", """#CONV.toJson('{"a": 2}')""", FailedAssertion("Expected: [{'a': 1}] but found [{'a': 2}]")),
      )
    ) { (expectedExpression, actualExpression, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          PredicateAssertion(AssertionOperator.Equals, expectedExpression.spel, actualExpression.spel),
        )
      )
      val nodesResultsAfterTestRun = prepareNodeResults(
        List(
          Map("someBigDecimal" -> new java.math.BigDecimal("1.0"))
        )
      )

      val results = verifyForTestCase(testCase, nodesResultsAfterTestRun)

      results shouldBe List(expectedResult)
    }
  }

  private def verifyForTestCase(
      testCase: TestCase,
      nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]]
  ): List[AssertionResult] = {
    val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)
    val compiledTestCase = assertionsCompiler
      .compile(testCase, scenarioTyping, jobData)
      .fold(errors => throw new IllegalStateException(s"Test compilation errors: $errors"), identity)
    val results = assertionVerifier.verify(
      compiledTestCase,
      nodesResultsAfterTestRun,
      jobData
    )
    results(nodeId)
  }

  private def prepareTestCase(assertions: List[Assertion]): TestCase = {
    TestCase(
      id = UUID.randomUUID(),
      name = "dummy",
      inputs = "dummy",
      mocks = Map.empty,
      assertions = Map(nodeId -> assertions)
    )
  }

  private def prepareNodeResults(variablesForEachRun: List[Map[String, Any]]): Map[NodeId, List[ResultContext[Any]]] = {
    Map(nodeId -> variablesForEachRun.map(variables => ResultContext[Any](ContextId.dummy, Instant.now(), variables)))
  }

}
