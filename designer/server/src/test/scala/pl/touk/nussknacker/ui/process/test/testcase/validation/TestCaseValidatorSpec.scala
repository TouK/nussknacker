package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.scalatest.{Inside, Inspectors, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, Filter}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.PredicateAssertion
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationErrorType, UIGlobalError}
import pl.touk.nussknacker.restmodel.validation.testcase.{
  AssertionValidationError,
  EnricherMockValidationError,
  NodeTestCaseValidationErrors
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{NodeTestCase, NodeTestCases}
import pl.touk.nussknacker.ui.config.TestCasesSettings
import pl.touk.nussknacker.ui.process.test.testcase.validation.TestCaseValidator.NodeTyping

import java.util.UUID
import scala.concurrent.Future

class TestCaseValidatorSpec extends AnyFunSuite with Matchers with Inside with OptionValues with Inspectors {

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

  private val testCaseValidator                = TestCaseValidator(modelData, TestCasesSettings())
  private val testCaseValidatorMultipleEnabled = TestCaseValidator(modelData, TestCasesSettings(multipleEnabled = true))

  private val jobData = JobData(MetaData("someScenario", StreamMetaData()), ProcessVersion.empty)

  private implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
    new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

  private val enricher = Enricher(
    id = NodeId("testEnricher"),
    name = NodeName("testEnricher"),
    service = ServiceRef("testEnricher", List(NodeParameter(ParameterName("par1"), "'test'".spel))),
    output = "enricherOutput"
  )

  private val inputVariableTypes = Map("input" -> Typed[String])

  test("should validate assertions") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "'expected'".spel,
            "#records[0].input".spel,
          ),
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#records.size".spel, description = None)
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes
      )
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
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes + (enricher.output -> Typed[String])
      )
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
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes + (enricher.output -> Typed[String])
      )
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
      id = NodeId("filter1"),
      name = NodeName("filter1"),
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
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes
      )
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
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "'value'".spel,
            "#doesNotExist".spel,
          )
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes
      )
    )

    result shouldBe Map(
      "test1" -> NodeTestCaseValidationErrors(
        enricherMockErrors = None,
        assertionsErrors = Some(
          Map(
            0 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "Unresolved reference 'doesNotExist'",
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(0, 0), TextCoordinates(13, 0))),
                fieldName = Some("actual"),
              )
            )
          )
        )
      )
    )
  }

  test("should ignore enricher mock with empty expression") {
    val nodeTestCases: NodeTestCases = Map(
      "test1" -> NodeTestCase(
        enricherMock = Some(EnricherMock(Expression.spel("  "))),
        assertions = List.empty
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes + (enricher.output -> Typed[String])
      )
    )

    result shouldBe Map.empty
  }

  test("should validate multiple test cases") {
    val nodeTestCases: NodeTestCases = Map(
      "validTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("'valid mock'".spel)),
        assertions = List(
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#records.size".spel, description = None)
        )
      ),
      "invalidMockTest" -> NodeTestCase(
        enricherMock = Some(EnricherMock("#invalidVar".spel)),
        assertions = List.empty
      ),
      "invalidAssertionTest" -> NodeTestCase(
        enricherMock = None,
        assertions = List(
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "1".spel,
            "#records[0].doesNotExist".spel,
          ),
          PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#records.size".spel, description = None),
          PredicateAssertion(
            Assertion.AssertionOperator.Equals,
            "2".spel,
            "#records[0].doesNotExistOther".spel,
          ),
        )
      )
    )

    val result = testCaseValidator.validateNodeTestCases(
      enricher,
      nodeTestCases,
      NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes + (enricher.output -> Typed[String])
      )
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
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(12, 0), TextCoordinates(24, 0))),
                fieldName = Some("actual"),
              )
            ),
            2 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doesNotExistOther' in type: Record{input: String}",
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(12, 0), TextCoordinates(29, 0))),
                fieldName = Some("actual"),
              )
            )
          )
        )
      )
    )
  }

  test("should validate multiple test cases at scenario level") {
    val filter = Filter(
      id = NodeId("filter1"),
      name = NodeName("filter1"),
      expression = Expression.spel("true"),
      isDisabled = None
    )

    val nodes = List(enricher, filter)
    val nodesTyping = Map(
      enricher.id.value -> NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes + (enricher.output -> Typed[String])
      ),
      filter.id.value -> NodeTyping(
        inputVariables = inputVariableTypes,
        outputVariables = inputVariableTypes
      )
    )

    val testCases = List(
      TestCase(
        id = UUID.randomUUID(),
        name = "validTest",
        inputs = "{}",
        mocks = Map(enricher.id -> EnricherMock("'valid mock'".spel)),
        assertions = Map(
          enricher.id -> List(
            PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#records.size".spel, description = None)
          ),
          filter.id -> List(
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'test'".spel,
              "#records[0].input".spel,
              description = None
            )
          )
        )
      ),
      TestCase(
        id = UUID.randomUUID(),
        name = "invalidMockTest",
        inputs = "{}",
        mocks = Map(
          enricher.id -> EnricherMock("42".spel),
          filter.id   -> EnricherMock("'not able to mock filter'".spel),
        ),
        assertions = Map.empty
      ),
      TestCase(
        id = UUID.randomUUID(),
        name = "invalidAssertionTest",
        inputs = "{}",
        mocks = Map.empty,
        assertions = Map(
          enricher.id -> List(
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "1".spel,
              "#records[0].doesNotExist".spel,
            ),
            PredicateAssertion(Assertion.AssertionOperator.Equals, "1".spel, "#records.size".spel, description = None),
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "2".spel,
              "#records[0].doesNotExistOther".spel,
            )
          ),
          filter.id -> List(
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "'value'".spel,
              "#records[0].input".spel,
            ),
            PredicateAssertion(
              Assertion.AssertionOperator.Equals,
              "1".spel,
              "#records[0].doesNotExist".spel,
            ),
          )
        )
      ),
    )

    val result = testCaseValidatorMultipleEnabled.validateScenarioTestCases(nodes, nodesTyping, testCases)

    val testCasesErrors = result.errors.testCasesValidationErrors.value
    testCasesErrors.keySet should contain only (enricher.id, filter.id)
    testCasesErrors(enricher.id) shouldBe Map(
      "invalidMockTest" -> NodeTestCaseValidationErrors(
        enricherMockErrors = Some(
          NonEmptyList.one(
            EnricherMockValidationError(
              typ = "ExpressionParserCompilationError",
              message = "Bad expression type, expected: String, found: Integer(42)",
              description = "There is problem with expression in field [mockExpression] - it could not be parsed.",
              details = None,
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
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(12, 0), TextCoordinates(24, 0))),
                fieldName = Some("actual"),
              )
            ),
            2 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doesNotExistOther' in type: Record{input: String}",
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(12, 0), TextCoordinates(29, 0))),
                fieldName = Some("actual"),
              )
            )
          )
        )
      )
    )
    testCasesErrors(filter.id) shouldBe Map(
      "invalidMockTest" -> NodeTestCaseValidationErrors(
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
      ),
      "invalidAssertionTest" -> NodeTestCaseValidationErrors(
        enricherMockErrors = None,
        assertionsErrors = Some(
          Map(
            1 -> NonEmptyList.one(
              AssertionValidationError(
                typ = "ExpressionParserCompilationError",
                message = "There is no property 'doesNotExist' in type: Record{input: String}",
                description = "There is problem with expression in field [actual] - it could not be parsed.",
                details = Some(CoordinatesBasedTextRange(TextCoordinates(12, 0), TextCoordinates(24, 0))),
                fieldName = Some("actual"),
              )
            )
          )
        )
      )
    )
  }

  test("should return an error when multiple test cases are not enabled") {
    val testCases = List(
      TestCase(id = UUID.randomUUID(), name = "test1", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "test2", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
    )

    val result = testCaseValidator.validateTestCaseBeforeResolving(testCases)

    result.errors.globalErrors should have size 1
    val error = result.errors.globalErrors.head.error
    error.typ shouldBe "MultipleTestCasesDisabled"
    error.message shouldBe "Multiple test cases are not available"
    error.errorType shouldBe NodeValidationErrorType.SaveNotAllowed
    result.errors.testCasesValidationErrors shouldBe None
  }

  test("should return a single error listing all duplicated test case names") {
    val testCases = List(
      TestCase(id = UUID.randomUUID(), name = "duplicateA", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "duplicateB", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "uniqueName", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "duplicateA", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "duplicateB", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
    )

    val result = testCaseValidatorMultipleEnabled.validateTestCaseBeforeResolving(testCases)

    result.errors.globalErrors should have size 1
    val error = result.errors.globalErrors.head.error
    error.typ shouldBe "DuplicateTestCaseNames"
    error.message shouldBe "Duplicate test case names: duplicateA, duplicateB"
    error.errorType shouldBe NodeValidationErrorType.SaveAllowed
    result.errors.testCasesValidationErrors shouldBe None
  }

  test("should return errors for test case names exceeding maximum length") {
    val longName1 = "a" * (TestCaseValidator.MaxTestCaseNameLength + 1)
    val longName2 = "b" * (TestCaseValidator.MaxTestCaseNameLength + 1)
    val testCases = List(
      TestCase(id = UUID.randomUUID(), name = longName1, inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = "validName", inputs = "{}", mocks = Map.empty, assertions = Map.empty),
      TestCase(id = UUID.randomUUID(), name = longName2, inputs = "{}", mocks = Map.empty, assertions = Map.empty)
    )

    val result = testCaseValidatorMultipleEnabled.validateTestCaseBeforeResolving(testCases)

    result.errors.globalErrors should have size 2
    forAll(result.errors.globalErrors) { case UIGlobalError(error, _) =>
      error.typ shouldBe "TestCaseNameTooLong"
      error.message should startWith("Test case name too long:")
      error.description shouldBe s"Test case name must not exceed ${TestCaseValidator.MaxTestCaseNameLength} characters"
      error.errorType shouldBe NodeValidationErrorType.SaveAllowed
    }
    result.errors.testCasesValidationErrors shouldBe None
  }

}
