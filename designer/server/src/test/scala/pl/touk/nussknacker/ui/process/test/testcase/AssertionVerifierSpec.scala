package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData, NodeId, NodeName, ProcessVersion, StreamMetaData}
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
import pl.touk.nussknacker.engine.testmode.TestProcess.{NodeTransition, ResultContext}
import pl.touk.nussknacker.engine.util.functions.conversion
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.process.test.testcase

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

  private val inputVariableTypes = Map(
    "someVariable"   -> Typed.fromInstance("bar"),
    "someJavaList"   -> Typed.fromInstance(new util.ArrayList[String]()),
    "someArray"      -> Typed.fromInstance(new Array[String](1)),
    "someBigDecimal" -> Typed[java.math.BigDecimal],
    "someNumber"     -> Typed[java.lang.Integer],
    "someCollection" -> Typed.fromInstance(new util.ArrayList[String]()),
  )

  private val scenarioTyping: Map[String, NodeTyping] = Map(
    nodeId.value -> testcase.NodeTyping(
      inputVariables = inputVariableTypes,
      outputVariables = inputVariableTypes + ("enricherOutput" -> Typed[String]),
    )
  )

  test("should run assertions on test nodes results and return assertion result for each assertion") {
    val testCase = prepareTestCase(
      List(
        ExpressionAssertion("#TESTS.assertEquals('valid', #records[0].someVariable)".spel),
        ExpressionAssertion("#TESTS.assertEquals('valid', #records[1].someVariable)".spel),
        PredicateAssertion(
          AssertionOperator.Equals,
          "'valid'".spel,
          "#records[0].someVariable".spel,
        ),
        PredicateAssertion(
          AssertionOperator.Equals,
          "'valid'".spel,
          "#records[1].someVariable".spel,
        ),
        PredicateAssertion(
          AssertionOperator.NotEquals,
          "'valid'".spel,
          "#records[0].someVariable".spel,
        ),
        PredicateAssertion(
          AssertionOperator.NotEquals,
          "'valid'".spel,
          "#records[1].someVariable".spel,
        ),
      )
    )
    val nodesResultsAfterTestRun = prepareNodeResults(
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

  test("should run assertions on outgoing results") {
    val testCase = prepareTestCase(
      List(
        PredicateAssertion(AssertionOperator.HasSize, "2".spel, "#outgoingRecords".spel),
        PredicateAssertion(AssertionOperator.Equals, "'a'".spel, "#outgoingRecords[0].someVariable".spel),
        PredicateAssertion(AssertionOperator.Equals, "'b'".spel, "#outgoingRecords[1].someVariable".spel),
        PredicateAssertion(AssertionOperator.Equals, "'y'".spel, "#outgoingRecords[0].enricherOutput".spel),
        PredicateAssertion(AssertionOperator.Equals, "'z'".spel, "#outgoingRecords[1].enricherOutput".spel),
      )
    )
    val nodesResultsAfterTestRun = prepareNodeResults(
      inputVariablesForEachRun = List(
        Map("someVariable" -> "a"),
        Map("someVariable" -> "b"),
      ),
      outputVariablesForEachRun = List(
        Map("someVariable" -> "a", "enricherOutput" -> "y"),
        Map("someVariable" -> "b", "enricherOutput" -> "z"),
      )
    )

    val results = verifyForTestCase(testCase, nodesResultsAfterTestRun)

    results should contain only (SuccessfulAssertion)
  }

  test("should properly compare various types used in SpEL") {
    forAll(
      Table(
        ("actual expression", "expected expression", "expected assertion result"),
        ("'valid'", "'valid'", SuccessfulAssertion),
        ("{}", "{}", SuccessfulAssertion),
        ("{:}", "{:}", SuccessfulAssertion),
        ("'abc'", "#CONV.toAny('abc')", SuccessfulAssertion),
        ("#records[0].someJavaList", "{'foo'}", SuccessfulAssertion),
        ("{'foo'}", "{'foo'}", SuccessfulAssertion),
        ("{'foo': 'bar'}", "{'foo': 'bar'}", SuccessfulAssertion),
        ("1L", "1", SuccessfulAssertion),
        ("null", "null", SuccessfulAssertion),
        ("{}", "{'foo'}", FailedAssertion("Expected: [{'foo'}] but found [{}]")),
        ("'1,2,3'.split(',')", "'1,2,3'.split(',')", SuccessfulAssertion), // comparing arrays
        (
          "'1,2,3'.split(',')",
          "'1,2'.split(',')",
          FailedAssertion("Expected: [{'1', '2'}] but found [{'1', '2', '3'}]")
        ),
        ("{:}", "{'a': 1}", FailedAssertion("Expected: [{'a': 1}] but found [{:}]")),
      )
    ) { (actualExpression, expectedExpression, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          ExpressionAssertion(s"#TESTS.assertEquals($expectedExpression, $actualExpression)".spel),
          PredicateAssertion(
            AssertionOperator.Equals,
            expectedExpression.spel,
            actualExpression.spel,
          ),
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
        ("actual expression", "expected expression", "expected assertion result"),
        ("#records[0].someBigDecimal", "1", SuccessfulAssertion),
        ("#records[0].someBigDecimal", "1L", SuccessfulAssertion),
        ("#records[0].someBigDecimal", "1.0", SuccessfulAssertion),
        ("#records[0].someBigDecimal", "1.0f", SuccessfulAssertion),
        ("T(java.math.BigDecimal).ONE", "1", SuccessfulAssertion),
        ("""#CONV.toJson('{"a": 1}')""", "{a: 1}", SuccessfulAssertion),
        ("""#CONV.toJson('{"a": 2}')""", "{a: 1}", FailedAssertion("Expected: [{'a': 1}] but found [{'a': 2}]")),
      )
    ) { (actualExpression, expectedExpression, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          PredicateAssertion(
            AssertionOperator.Equals,
            expectedExpression.spel,
            actualExpression.spel,
          ),
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

  test("should verify comparison operators") {
    forAll(
      Table(
        ("operator", "actualValue", "expectedExpr", "expectedResult"),
        (AssertionOperator.GreaterThan, 15, "10", SuccessfulAssertion),
        (AssertionOperator.LessThan, 10, "10", FailedAssertion("Expected: [10] < [10]")),
        (AssertionOperator.GreaterThanOrEqual, 10, "10", SuccessfulAssertion),
        (AssertionOperator.LessThanOrEqual, 15, "10", FailedAssertion("Expected: [10] <= [15]")),
      )
    ) { (operator, actualValue, expectedExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          PredicateAssertion(operator, expectedExpr.spel, "#records[0].someNumber".spel)
        )
      )
      val nodesResultsAfterTestRun = prepareNodeResults(List(Map("someNumber" -> actualValue)))
      val results                  = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should verify hasSize operator") {
    forAll(
      Table(
        ("actualList", "expectedSizeExpr", "expectedResult"),
        (java.util.List.of("a", "b", "c"), "3", SuccessfulAssertion),
        (java.util.List.of("a", "b", "c"), "2", FailedAssertion("Expected size: [2] but found: [3]")),
        (new java.util.ArrayList[String](), "0", SuccessfulAssertion),
        (java.util.Map.of("k1", "v1", "k2", "v2"), "2", SuccessfulAssertion),
        (java.util.Map.of("k1", "v1", "k2", "v2"), "1", FailedAssertion("Expected size: [1] but found: [2]")),
      )
    ) { (actualList, expectedSizeExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(PredicateAssertion(AssertionOperator.HasSize, expectedSizeExpr.spel, "#records[0].someCollection".spel))
      )
      val nodesResultsAfterTestRun = prepareNodeResults(List(Map("someCollection" -> actualList)))
      val results                  = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should verify contains operator for collections") {
    forAll(
      Table(
        ("actualCollection", "expectedExpr", "expectedResult"),
        (java.util.List.of("a", "b", "c"), "'b'", SuccessfulAssertion),
        (java.util.List.of("a", "b", "c"), "'d'", FailedAssertion("Expected [{'a', 'b', 'c'}] to contain ['d']")),
      )
    ) { (actualCollection, expectedExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(PredicateAssertion(AssertionOperator.Contains, expectedExpr.spel, "#records[0].someCollection".spel))
      )
      val nodesResultsAfterTestRun = prepareNodeResults(List(Map("someCollection" -> actualCollection)))
      val results                  = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should verify contains operator for strings") {
    forAll(
      Table(
        ("actualString", "expectedExpr", "expectedResult"),
        ("hello world", "'ello'", SuccessfulAssertion),
        ("hello world", "'xyz'", FailedAssertion("Expected ['hello world'] to contain ['xyz']")),
      )
    ) { (actualString, expectedExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(PredicateAssertion(AssertionOperator.Contains, expectedExpr.spel, "#records[0].someVariable".spel))
      )
      val nodesResultsAfterTestRun = prepareNodeResults(List(Map("someVariable" -> actualString)))
      val results                  = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should verify matches operator") {
    forAll(
      Table(
        ("actualString", "patternExpr", "expectedResult"),
        ("ABC-123", "'[A-Z]{3}-\\d+'", SuccessfulAssertion),
        ("abc-123", "'[A-Z]{3}-\\d+'", FailedAssertion("Expected ['abc-123'] to match ['[A-Z]{3}-\\d+']")),
        ("hello world", "'.*world.*'", SuccessfulAssertion),
        ("hello universe", "'.*world.*'", FailedAssertion("Expected ['hello universe'] to match ['.*world.*']")),
      )
    ) { (actualString, patternExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(PredicateAssertion(AssertionOperator.Matches, patternExpr.spel, "#records[0].someVariable".spel))
      )
      val nodesResultsAfterTestRun = prepareNodeResults(List(Map("someVariable" -> actualString)))
      val results                  = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should handle null values gracefully") {
    forAll(
      Table(
        ("operator", "actualExpr", "expectedExpr", "expectedResult"),
        (AssertionOperator.GreaterThan, "#records[0].someNumber", "10", FailedAssertion("Actual value is null")),
        (AssertionOperator.LessThan, "#records[0].someNumber", "10", FailedAssertion("Actual value is null")),
        (AssertionOperator.GreaterThanOrEqual, "#records[0].someNumber", "10", FailedAssertion("Actual value is null")),
        (AssertionOperator.LessThanOrEqual, "#records[0].someNumber", "10", FailedAssertion("Actual value is null")),
        (AssertionOperator.Contains, "#records[0].someVariable", "'x'", FailedAssertion("Actual value is null")),
        (AssertionOperator.Matches, "#records[0].someVariable", "'.*'", FailedAssertion("Actual value is null")),
        (AssertionOperator.HasSize, "#records[0].someCollection", "3", FailedAssertion("Actual value is null")),
        (AssertionOperator.HasSize, "{'a', 'b'}", "#records[0].someNumber", FailedAssertion("Expected value is null")),
      )
    ) { (operator, actualExpr, expectedExpr, expectedResult) =>
      val testCase = prepareTestCase(
        List(PredicateAssertion(operator, expectedExpr.spel, actualExpr.spel))
      )
      val nodesResultsAfterTestRun =
        prepareNodeResults(List(Map("someNumber" -> null, "someVariable" -> null, "someCollection" -> null)))
      val results = verifyForTestCase(testCase, nodesResultsAfterTestRun)
      results shouldBe List(expectedResult)
    }
  }

  test("should not evaluate empty assertions") {
    forAll(
      Table(
        ("actual expression", "expected expression", "expected assertion result"),
        ("", "", SuccessfulAssertion),
        ("    ", "  ", SuccessfulAssertion),
        ("#records[0].someBigDecimal", "", SuccessfulAssertion),
        ("#records[0].someBigDecimal", "   ", SuccessfulAssertion),
        ("  ", "{a: 1}", SuccessfulAssertion),
      )
    ) { (actualExpression, expectedExpression, expectedResult) =>
      val testCase = prepareTestCase(
        List(
          PredicateAssertion(
            AssertionOperator.Equals,
            expectedExpression.spel,
            actualExpression.spel,
          ),
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
      testResults: AssertionVerifier.TestResults
  ): List[AssertionResult] = {
    val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)
    val compiledTestCase = assertionsCompiler
      .compile(testCase, scenarioTyping, jobData, Map(nodeId -> NodeName(nodeId.value)))
      .fold(errors => throw new IllegalStateException(s"Test compilation errors: $errors"), compiled => compiled)
    val results = assertionVerifier.verify(
      compiledTestCase,
      testResults,
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

  private def prepareNodeResults(
      inputVariablesForEachRun: List[Map[String, Any]] = Nil,
      outputVariablesForEachRun: List[Map[String, Any]] = Nil
  ): AssertionVerifier.TestResults = {
    AssertionVerifier.TestResults(
      originalNodeResults = Map(nodeId -> inputVariablesForEachRun.map(dummyResultContext)),
      originalNodeTransitionResults =
        Map(NodeTransition(nodeId, Some(NodeId("someOtherNode"))) -> outputVariablesForEachRun.map(dummyResultContext)),
    )
  }

  private def dummyResultContext(variables: Map[String, Any]): ResultContext[Any] =
    ResultContext(ContextId.dummy, Instant.now(), variables)
}
