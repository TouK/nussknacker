package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData, NodeId, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
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
      GlobalVariablesPreparer.apply(modelDefinitionWithClasses.modelDefinition.expressionConfig)
    )
  )

  private val testCaseGlobalVariablesPreparer = GlobalVariablesPreparer(modelDefinitionWithClasses.modelDefinition.expressionConfig)
  private val testCompiler = new AssertionsCompiler(expressionCompiler, testCaseGlobalVariablesPreparer)
  private val verifier = new AssertionVerifier(testCaseGlobalVariablesPreparer)

  private val scenarioTyping: Map[String, NodeTypingData] = Map(
    "someNode" -> NodeTypingData(
      Map(
        "someVariable" -> Typed.fromInstance("bar"),
        "someJavaList" -> Typed.fromInstance(new util.ArrayList[String]()),
        "someArray" -> Typed.fromInstance(new Array[String](1))
      ), None, Map.empty
    )
  )

  test("should run assertions on test nodes results and return assertion result for each assertion") {
    val testCase = TestCase(
      UUID.randomUUID(),
      "dummy",
      "dummy",
      Map.empty,
      Map(
        NodeId("someNode") -> List(
          Assertion("#TESTS.assertEquals('valid', #contexts[0].someVariable)".spel),
          Assertion("#TESTS.assertEquals('valid', #contexts[1].someVariable)".spel),
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

  test("should properly compare various types used in SpEL") {
    forAll(Table(
      ("assertion", "result"),
      ("#TESTS.assertEquals('valid', 'valid')", SuccessfulAssertion),
      ("#TESTS.assertEquals({}, {})", SuccessfulAssertion),
      ("#TESTS.assertEquals({:}, {:})", SuccessfulAssertion),
      ("#TESTS.assertEquals(#CONV.toAny('abc'), 'abc')", SuccessfulAssertion),
      ("#TESTS.assertEquals({'foo'}, #contexts[0].someJavaList)", SuccessfulAssertion),
      ("#TESTS.assertEquals({'foo'}, {'foo'})", SuccessfulAssertion),
      ("#TESTS.assertEquals({'foo': 'bar'}, {'foo': 'bar'})", SuccessfulAssertion),
      ("#TESTS.assertEquals(1, 1L)", SuccessfulAssertion),
      ("#TESTS.assertEquals(null, null)", SuccessfulAssertion),
      ("#TESTS.assertEquals({'foo'}, {})", FailedAssertion("Expected: [{foo}] but found [{}]")),
      ("#TESTS.assertEquals('1,2,3'.split(','), '1,2,3'.split(','))", SuccessfulAssertion), // comparing arrays
      ("#TESTS.assertEquals('1,2'.split(','), '1,2,3'.split(','))", FailedAssertion("Expected: [{1, 2}] but found [{1, 2, 3}]")),
      ("#TESTS.assertEquals({'1','2','3'}, '1,2,3'.split(','))", SuccessfulAssertion), // comparing arrays with SpEL inline lists
      ("#TESTS.assertEquals({'a': 1}, {:})", FailedAssertion("Expected: [{a: 1}] but found [{:}]")),
      // ("#TESTS.assertEquals({'1,2'.split(',')}, {'1,2'.split(',')})", SuccessfulAssertion), //todo: to be discussed whether it should work for nested structures
    )) { (assertion, result) =>
      val testCase = TestCase(
        UUID.randomUUID(),
        "dummy",
        "dummy",
        Map.empty,
        Map(
          NodeId("someNode") -> List(
            Assertion(assertion.spel),
          )
        )
      )
      val nodesResultsAfterTestRun: Map[NodeId, List[ResultContext[Any]]] = Map(
        NodeId("someNode") -> List(
          ResultContext[Any](ContextId.dummy, Instant.now(), Map(
            "someJavaList" -> createSingletonArrayList("foo"),
          )),
        )
      )

      verifyForTestCase(testCase, nodesResultsAfterTestRun).toList shouldBe List(NodeId("someNode") -> List(result))
    }
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

  private def createSingletonArrayList[T](element: T): java.util.ArrayList[T] = {
    val list = new util.ArrayList[T]()
    list.add(element)
    list
  }

}
