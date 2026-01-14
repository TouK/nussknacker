package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
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
import pl.touk.nussknacker.restmodel.validation.testcase.{
  AssertionValidationError,
  EnricherMockValidationError,
  NodeTestCaseValidationErrors
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodeTestCase, NodeTestCases}

import scala.concurrent.Future

class TestCaseValidatorSpec extends AnyFunSuite with Matchers with Inside {

  object TestEnricher extends Service {
    @MethodToInvoke
    def invoke(@ParamName("par1") par1: String): Future[String] = ???
  }

  private val modelData = LocalModelData(
    ConfigFactory.empty(),
    List(
      ComponentDefinition("testEnricher", TestEnricher),
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

  private val enricher = Enricher(
    id = "testEnricher",
    service = ServiceRef("testEnricher", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
    output = "enricherOutput"
  )

  private val variableTypes = Map("input" -> Typed[String])

  test("should validate assertions") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#TESTS.assertEquals(#contexts[0].input, 'expected')".spel),
          Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      variableTypes,
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should validate enricher mock") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'mocked value'".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      variableTypes,
      jobData,
    )

    result shouldBe Map.empty
  }

  test("should return error for invalid enricher mock expression") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock("42".spel)),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      variableTypes,
      jobData,
    )

    result shouldBe Map(
      "test1" -> NodeTestCaseValidationErrors(
        enricherMockErrors = Some(
          NonEmptyList.one(
            EnricherMockValidationError(
              typ = "ExpressionParserCompilationError",
              message = "Bad expression type, expected: String, found: Integer(42)",
              description = "There is problem with expression in field [mockExpression] - it could not be parsed.",
              details = None
            ),
          )
        ),
        assertionsErrors = None
      )
    )

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
      variableTypes,
      jobData,
    )

    result shouldBe Map(
      "test1" -> NodeTestCaseValidationErrors(
        enricherMockErrors = Some(
          NonEmptyList.one(
            EnricherMockValidationError(
              typ = "MockForNonEnricherNode",
              message = "Mock configured for non-enricher node 'filter1'",
              description = "Mocks can only be configured for enricher nodes",
              details = None
            )
          )
        ),
        assertionsErrors = None
      )
    )
  }

  test("should return error for invalid assertion expression") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#TESTS.doSthMagic".spel)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      variableTypes,
      jobData,
    )

    result shouldBe Map(
      "test1" -> NodeTestCaseValidationErrors(
        enricherMockErrors = None,
        assertionsErrors = Some(
          Map(
            0 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doSthMagic' in type: tests",
                description = "There is problem with expression in field [<missing>] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(7, 0), TextCoordinates(17, 0)))
              )
            )
          )
        )
      )
    )
  }

  test("should validate multiple test cases") {
    val nodeTestCases: NodeTestCases = Map(
      "validTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'valid mock'".spel)),
        assertions = List(Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel))
      ),
      "invalidMockTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("#invalidVar".spel)),
        assertions = List.empty
      ),
      "invalidAssertionTest" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          Assertion("#TESTS.assertEquals(#contexts[0].doesNotExist, 1)".spel),
          Assertion("#TESTS.assertEquals(#contexts.size, 1)".spel),
          Assertion("#TESTS.assertEquals(#contexts[0].doesNotExistOther, 2)".spel),
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      variableTypes,
      jobData,
    )

    result shouldBe Map(
      "invalidMockTest" -> NodeTestCaseValidationErrors(
        enricherMockErrors = Some(
          NonEmptyList.one(
            EnricherMockValidationError(
              typ = "ExpressionParserCompilationError",
              message = "Unresolved reference 'invalidVar'",
              description = "There is problem with expression in field [mockExpression] - it could not be parsed.",
              details = Some(CoordinatesBasedTextRange(TextCoordinates(0, 0), TextCoordinates(11, 0)))
            )
          )
        ),
        assertionsErrors = None
      ),
      "invalidAssertionTest" -> NodeTestCaseValidationErrors(
        enricherMockErrors = None,
        assertionsErrors = Some(
          Map(
            0 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doesNotExist' in type: Record{input: String}",
                description = "There is problem with expression in field [<missing>] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(33, 0), TextCoordinates(45, 0)))
              )
            ),
            2 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doesNotExistOther' in type: Record{input: String}",
                description = "There is problem with expression in field [<missing>] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(33, 0), TextCoordinates(50, 0)))
              )
            )
          )
        )
      )
    )
  }

}
