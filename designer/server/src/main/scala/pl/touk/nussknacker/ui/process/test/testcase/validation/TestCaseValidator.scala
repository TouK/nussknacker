package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.all._
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.test.testcase.{TestCase, TestCaseName}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  UIGlobalError,
  ValidationResult
}
import pl.touk.nussknacker.restmodel.validation.testcase.{
  NodeTestCasesValidationErrors,
  NodeTestCaseValidationErrors,
  ScenarioTestCasesValidationErrors
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.config.TestCasesSettings
import pl.touk.nussknacker.ui.process.test.testcase.AssertionsCompiler

class TestCaseValidator(
    enricherMockValidator: EnricherMockValidator,
    assertionValidator: AssertionValidator,
    testCasesSettings: TestCasesSettings,
) {

  import TestCaseValidator._

  def validateTestCaseBeforeResolving(testCases: List[TestCase]): ValidationResult = {
    (
      validateNameLength(testCases),
      validateNameUniqueness(testCases),
      validateMultipleTestCasesAvailable(testCases),
    ).tupled.fold(
      errors => ValidationResult.errors(globalErrors = errors.toList),
      _ => ValidationResult.success
    )
  }

  private def validateNameLength(testCases: List[TestCase]): ValidatedNel[UIGlobalError, Unit] = {
    val tooLongNames      = testCases.filter(_.name.length > MaxTestCaseNameLength)
    val tooLongNameErrors = tooLongNames.map(tc => errors.testCaseNameTooLong(tc.name))
    NonEmptyList.fromList(tooLongNameErrors).toInvalid(())
  }

  private def validateNameUniqueness(testCases: List[TestCase]): ValidatedNel[UIGlobalError, Unit] = {
    val duplicateNames =
      testCases.groupBy(_.name).collect { case (name, occurrences) if occurrences.size > 1 => name }.toList
    Validated
      .cond(
        duplicateNames.isEmpty,
        (),
        errors.duplicateTestCaseNames(duplicateNames)
      )
      .toValidatedNel
  }

  private def validateMultipleTestCasesAvailable(testCases: List[TestCase]): ValidatedNel[UIGlobalError, Unit] = {
    Validated
      .cond(
        testCasesSettings.multipleEnabled || testCases.size <= 1,
        (),
        errors.multipleTestCasesNotAvailable
      )
      .toValidatedNel
  }

  def validateScenarioTestCases(
      nodes: List[NodeData],
      nodesTyping: Map[String, TestCaseValidator.NodeTyping],
      testCases: List[TestCase],
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ValidationResult = {
    val testCasesValidationErrors = validateScenarioTestCasesByNode(nodes, nodesTyping, testCases)
    ValidationResult.errors(
      testCasesValidationErrors = Option.when(testCasesValidationErrors.nonEmpty)(testCasesValidationErrors),
    )
  }

  private def validateScenarioTestCasesByNode(
      nodes: List[NodeData],
      nodesTyping: Map[String, TestCaseValidator.NodeTyping],
      testCases: List[TestCase]
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ScenarioTestCasesValidationErrors = {
    val nodesById = nodes.map(n => n.id -> n).toMap
    val testCasesErrorsByNode = nodesById.flatMap { case (nodeId, node) =>
      val nodeTestCases = prepareNodeTestCases(testCases, nodeId)
      val nodeTyping    = nodesTyping.getOrElse(nodeId.value, NodeTyping.empty)
      val errors        = validateNodeTestCases(node, nodeTestCases, nodeTyping)
      if (errors.isEmpty) None else Some(nodeId -> errors)
    }
    testCasesErrorsByNode
  }

  private def prepareNodeTestCases(
      testCases: List[TestCase],
      nodeId: NodeId
  ): NodeTestCases = {
    testCases.map { testCase =>
      testCase.name -> NodeTestCase(
        enricherMock = testCase.mocks.get(nodeId),
        assertions = testCase.assertions.getOrElse(nodeId, List.empty)
      )
    }.toMap
  }

  def validateNodeTestCases(
      nodeData: NodeData,
      nodeTestCases: NodeTestCases,
      nodeTyping: NodeTyping
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): NodeTestCasesValidationErrors = {
    nodeTestCases.flatMap { case (testCaseName, nodeTestCase) =>
      validateSingleNodeTestCase(
        nodeData,
        nodeTestCase,
        nodeTyping
      ) match {
        case Left(errors) => Some(testCaseName -> errors)
        case Right(_)     => None
      }
    }
  }

  private def validateSingleNodeTestCase(
      nodeData: NodeData,
      nodeTestCase: NodeTestCase,
      nodeTyping: NodeTyping
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[NodeTestCaseValidationErrors, Unit] = {
    val enricherMockErrors = enricherMockValidator.validateForNode(
      nodeData,
      nodeTestCase.enricherMock,
      nodeTyping
    )
    val assertionsErrors = assertionValidator.validateForNode(
      nodeTestCase.assertions,
      nodeTyping.inputVariables,
      scenarioCompilationDependencies.jobData
    )(nodeData.id, nodeData.name)

    (enricherMockErrors, assertionsErrors) match {
      case (None, None) => Right(())
      case (enricherErrs, assertionErrs) =>
        Left(NodeTestCaseValidationErrors(enricherErrs, assertionErrs))
    }
  }

}

object TestCaseValidator {

  private[validation] val MaxTestCaseNameLength = 100

  final case class NodeTyping(
      inputVariables: Map[String, TypingResult],
      outputVariables: Map[String, TypingResult],
  )

  object NodeTyping {
    val empty = NodeTyping(Map.empty, Map.empty)
  }

  def apply(modelData: ModelData, testCasesSettings: TestCasesSettings): TestCaseValidator = {
    val expressionCompiler      = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper
    val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
    new TestCaseValidator(
      new EnricherMockValidator(expressionCompiler, globalVariablesPreparer),
      new AssertionValidator(
        new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
      ),
      testCasesSettings,
    )
  }

  private object errors {

    def testCaseNameTooLong(invalidName: TestCaseName): UIGlobalError = {
      UIGlobalError(
        error = NodeValidationError(
          typ = "TestCaseNameTooLong",
          message = s"Test case name too long: '${invalidName.take(MaxTestCaseNameLength / 5)}...'",
          description = s"Test case name must not exceed $MaxTestCaseNameLength characters",
          fieldName = None,
          errorType = NodeValidationErrorType.SaveAllowed,
          details = None,
        ),
        nodeIds = List.empty,
      )
    }

    def duplicateTestCaseNames(duplicateNames: List[TestCaseName]): UIGlobalError = {
      UIGlobalError(
        error = NodeValidationError(
          typ = "DuplicateTestCaseNames",
          message = s"Duplicate test case names: ${duplicateNames.sorted.mkString(", ")}",
          description = "Test case names must be unique",
          fieldName = None,
          errorType = NodeValidationErrorType.SaveAllowed,
          details = None,
        ),
        nodeIds = List.empty,
      )
    }

    def multipleTestCasesNotAvailable: UIGlobalError =
      UIGlobalError(
        error = NodeValidationError(
          typ = "MultipleTestCasesDisabled",
          message = "Multiple test cases are not available",
          description = "Multiple test cases feature is disabled.",
          fieldName = None,
          errorType = NodeValidationErrorType.SaveNotAllowed,
          details = None,
        ),
        nodeIds = List.empty,
      )

  }

}
