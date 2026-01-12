package pl.touk.nussknacker.ui.process.test.testcase

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, Filter}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodeTestCase, NodeTestCases}

import scala.concurrent.Future

class TestCaseValidatorSpec extends AnyFunSuite with Matchers with Inside {

  object Enricher1Service extends Service {
    @MethodToInvoke
    def invoke(@ParamName("par1") par1: String): Future[String] = ???
  }

  object Enricher2Service extends Service {
    @MethodToInvoke
    def invoke(@ParamName("value") value: Int): Future[Int] = ???
  }

  private val modelData = LocalModelData(
    ConfigFactory.empty(),
    List(
      ComponentDefinition("enricher1", Enricher1Service),
      ComponentDefinition("enricher2", Enricher2Service)
    )
  )

  private val globalVariablesPreparer =
    GlobalVariablesPreparer.apply(modelData.modelDefinition.expressionConfig)

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private val assertionsCompiler = new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)

  private val testCaseValidator = new TestCaseValidator(modelData, assertionsCompiler)

  private val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)

  private implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
    new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

  test("should successfully validate valid enricher mock") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'mocked value'".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should return error for invalid enricher mock expression") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("#invalidVariable".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result should have size 1
    result("test1").enricherMockError shouldBe defined
    // Error is expected for undefined variable
    result("test1").enricherMockError.get.head.message should include("invalidVariable")
  }

  test("should allow enricher mock with compatible type") {
    val enricher = Enricher(
      id = "enricher2",
      service = ServiceRef("enricher2", List(NodeParameter(ParameterName("value"), "42".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("123".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should return error for mock on non-enricher node") {
    val filter = Filter(
      id = "filter1",
      expression = Expression.spel("true"),
      isDisabled = None
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'value'".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      filter,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result should have size 1
    result("test1").enricherMockError shouldBe defined
    result("test1").enricherMockError.get.head.typ shouldBe "MockForNonEnricherNode"
    result("test1").enricherMockError.get.head.message should include("non-enricher node")
  }

  test("should successfully validate valid assertions") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#TESTS.assertEquals('expected', 'expected')".spel),
          Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map("input" -> Typed[String]),
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should return error for invalid assertion expression") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#invalidVariable".spel)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result should have size 1
    result("test1").assertionsErrors shouldBe defined
    result("test1").assertionsErrors.get should have size 1
    // Error is expected for undefined variable (either IllegalPropertyName or NotFoundError)
    result("test1").assertionsErrors.get(0).head.message should include("invalidVariable")
  }

  test("should validate multiple test cases with mixed valid and invalid") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "validTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'valid mock'".spel)),
        assertions = List(Assertion("#TESTS.assertEquals(1, 1)".spel))
      ),
      "invalidMockTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("#invalidVar".spel)),
        assertions = List.empty
      ),
      "invalidAssertionTest" -> NodeTestCase(
        enricherMock = None,
        assertions = List(Assertion("#badExpression".spel))
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result should have size 2
    result.keySet should contain only ("invalidMockTest", "invalidAssertionTest")
    result("invalidMockTest").enricherMockError shouldBe defined
    result("invalidAssertionTest").assertionsErrors shouldBe defined
  }

  test("should return empty map for test cases with no errors") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map.empty,
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should validate assertions with available variables") {
    val enricher = Enricher(
      id = "enricher1",
      service = ServiceRef("enricher1", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
      output = "enricherOutput"
    )
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#TESTS.assertEquals(#contexts[0].input, 'test')".spel)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      Map("input" -> Typed[String]),
      jobData,
    )

    result shouldBe Map.empty
  }

}
