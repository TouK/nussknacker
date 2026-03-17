package pl.touk.nussknacker.ui.process.test.testcase.validation

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
import pl.touk.nussknacker.ui.process.test.testcase.AssertionsCompiler

class TestCaseValidator(
    enricherMockValidator: EnricherMockValidator,
    assertionValidator: AssertionValidator,
) {

  import TestCaseValidator._

  def validateScenarioTestCases(
      nodes: List[NodeData],
      nodesTyping: Map[String, TestCaseValidator.NodeTyping],
      testCases: List[TestCase],
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ValidationResult = {
    val globalErrors              = validateNameUniqueness(testCases)
    val testCasesValidationErrors = validateScenarioTestCasesByNode(nodes, nodesTyping, testCases)
    ValidationResult.errors(
      globalErrors = globalErrors,
      testCasesValidationErrors = Option.when(testCasesValidationErrors.nonEmpty)(testCasesValidationErrors),
    )
  }

  private def validateNameUniqueness(testCases: List[TestCase]): List[UIGlobalError] = {
    val duplicateNames =
      testCases.groupBy(_.name).collect { case (name, occurrences) if occurrences.size > 1 => name }.toList
    Option
      .when(duplicateNames.nonEmpty) {
        errors.duplicateTestCaseNames(duplicateNames)
      }
      .toList
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

  final case class NodeTyping(
      inputVariables: Map[String, TypingResult],
      outputVariables: Map[String, TypingResult],
  )

  object NodeTyping {
    val empty = NodeTyping(Map.empty, Map.empty)
  }

  def apply(modelData: ModelData): TestCaseValidator = {
    val expressionCompiler      = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper
    val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
    new TestCaseValidator(
      new EnricherMockValidator(expressionCompiler, globalVariablesPreparer),
      new AssertionValidator(
        new AssertionsCompiler(expressionCompiler, globalVariablesPreparer)
      )
    )
  }

  private object errors {

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

  }

}
