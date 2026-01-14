package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated._
import cats.syntax.functor._
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCase}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.restmodel.validation.testcase.{
  AssertionIndex,
  AssertionValidationError,
  EnricherMockValidationError,
  NodeTestCasesValidationErrors,
  NodeTestCaseValidationErrors,
  ScenarioTestCasesValidationErrors
}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.AssertionExpressionCompilationError

class TestCaseValidator(
    modelData: ModelData,
    assertionsCompiler: AssertionsCompiler
) {

  import TestCaseValidator._

  private val expressionCompiler      = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper
  private val globalVariablesPreparer = GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
  private val nodeCompiler            = NodeCompiler.forValidation(modelData)

  def validateScenarioTestCases(
      nodes: List[NodeData],
      nodesTyping: Map[String, NodeTypingData],
      testCases: List[TestCase],
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ScenarioTestCasesValidationErrors = {

    val nodesById = nodes.map(n => NodeId(n.id) -> n).toMap
    val nodeTestCasesErrors = nodesById.flatMap { case (nodeId, node) =>
      val nodeTestCases = testCases.flatMap { testCase =>
        val hasMock       = testCase.mocks.contains(nodeId)
        val hasAssertions = testCase.assertions.get(nodeId).exists(_.nonEmpty)

        if (hasMock || hasAssertions) {
          Some(
            // TODO: do not use DTO here.
            testCase.name -> NodeTestCase(
              enricherMock = testCase.mocks.get(nodeId),
              assertions = testCase.assertions.getOrElse(nodeId, List.empty)
            )
          )
        } else {
          None
        }
      }.toMap

      if (nodeTestCases.nonEmpty) {
        val variableTypes = nodesTyping.get(nodeId.id).map(_.variableTypes).getOrElse(Map.empty)

        val errors = validateNodeTestCases(
          node,
          nodeTestCases,
          variableTypes,
          scenarioCompilationDependencies.jobData
        )

        if (errors.isEmpty) None else Some(nodeId -> errors)
      } else {
        None
      }
    }

    nodeTestCasesErrors
  }

  def validateNodeTestCases(
      nodeData: NodeData,
      nodeTestCases: NodeTestCases,
      variableTypes: Map[String, TypingResult],
      jobData: JobData,
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): NodeTestCasesValidationErrors = {
    nodeTestCases.flatMap { case (testCaseName, nodeTestCase) =>
      validateSingleNodeTestCase(
        nodeData,
        nodeTestCase,
        variableTypes,
        jobData,
      ) match {
        case Left(errors) => Some(testCaseName -> errors)
        case Right(_)     => None
      }
    }
  }

  private def validateSingleNodeTestCase(
      nodeData: NodeData,
      nodeTestCase: NodeTestCase,
      variableTypes: Map[String, TypingResult],
      jobData: JobData,
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Either[NodeTestCaseValidationErrors, Unit] = {
    val enricherMockErrors = validateEnricherMock(
      nodeData,
      nodeTestCase.enricherMock,
      variableTypes,
    )
    val assertionsErrors = validateAssertions(
      NodeId(nodeData.id),
      nodeTestCase.assertions,
      variableTypes,
      jobData
    )

    (enricherMockErrors, assertionsErrors) match {
      case (None, None) => Right(())
      case (enricherErrs, assertionErrs) =>
        Left(NodeTestCaseValidationErrors(enricherErrs, assertionErrs))
    }
  }

  private def validateEnricherMock(
      nodeData: NodeData,
      enricherMock: Option[EnricherMock],
      variableTypes: Map[String, TypingResult],
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Option[NonEmptyList[EnricherMockValidationError]] = {
    enricherMock match {
      case Some(mock) =>
        nodeData match {
          case enricher: Enricher =>
            compileEnricherMock(enricher, mock.expression, variableTypes) match {
              case Invalid(errors) => Some(errors)
              case Valid(_)        => None
            }
          case _ =>
            Some(NonEmptyList.one(errors.enricherMockForNonEnricherNode(nodeData)))
        }
      case None => None
    }
  }

  // TODO: consider reusing compilation result from NodeDataValidator to avoid double compilation of the enricher node
  private def compileEnricherMock(
      enricher: Enricher,
      mockExpression: Expression,
      variableTypes: Map[String, TypingResult],
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidatedNel[EnricherMockValidationError, Unit] = {
    implicit val nodeId: NodeId = NodeId(enricher.id)
    val validationContextWithGlobalVariablesOnly =
      globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(scenarioCompilationDependencies.jobData)
    val inputContext = validationContextWithGlobalVariablesOnly.copy(localVariables = variableTypes)
    val enricherCompilationResult = modelData.withModelClassloaderAsContextClassLoader {
      nodeCompiler.compileNode(enricher, variableTypes, branchVariableTypes = None, outgoingEdges = List.empty)
    }
    val expectedType = enricherCompilationResult.validationContext
      .leftMap(convertToEnricherMockErrors)
      .andThen { valCtx =>
        Validated
          .fromOption(
            valCtx.localVariables.get(enricher.output),
            errors.missingEnricherOutputVariable(enricher)
          )
          .toValidatedNel
      }
    expectedType
      .andThen { typ =>
        expressionCompiler
          .compile(
            mockExpression,
            Some(ParameterName("mockExpression")),
            inputContext,
            typ
          )
          .void
          .leftMap(convertToEnricherMockErrors)
      }
  }

  private def validateAssertions(
      nodeId: NodeId,
      assertions: List[Assertion],
      variableTypes: Map[String, TypingResult],
      jobData: JobData
  ): Option[Map[AssertionIndex, NonEmptyList[AssertionValidationError]]] = {
    if (assertions.isEmpty) {
      None
    } else {
      val compilationResults = assertionsCompiler.compileForNode(nodeId, assertions, variableTypes, jobData)
      val errorsMap = compilationResults.zipWithIndex.collect { case (Invalid(error), index) =>
        index -> convertToAssertionErrors(error)
      }.toMap
      Some(errorsMap).filter(_.nonEmpty)
    }
  }

  private def convertToEnricherMockErrors(
      errors: NonEmptyList[ProcessCompilationError],
  ): NonEmptyList[EnricherMockValidationError] = {
    errors.map { error =>
      val prettyError = PrettyValidationErrors.formatErrorMessage(error)
      EnricherMockValidationError(
        typ = prettyError.typ,
        message = prettyError.message,
        description = prettyError.description,
        details = prettyError.details
      )
    }
  }

  private def convertToAssertionErrors(
      error: AssertionExpressionCompilationError
  ): NonEmptyList[AssertionValidationError] = {
    error.errors.map { compilationError =>
      val prettyError = PrettyValidationErrors.formatErrorMessage(compilationError)
      AssertionValidationError(
        typ = prettyError.typ,
        message = prettyError.message,
        description = prettyError.description,
        details = prettyError.details
      )
    }
  }

}

object TestCaseValidator {

  def apply(modelData: ModelData): TestCaseValidator = {
    new TestCaseValidator(
      modelData,
      new AssertionsCompiler(
        ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper,
        GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      )
    )
  }

  private object errors {

    def enricherMockForNonEnricherNode(nodeData: NodeData): EnricherMockValidationError =
      EnricherMockValidationError(
        typ = "MockForNonEnricherNode",
        message = s"Mock configured for non-enricher node '${nodeData.id}'",
        description = "Mocks can only be configured for enricher nodes",
        details = None
      )

    // It should not happen because we compile the enricher node which ensures the output variable exists.
    def missingEnricherOutputVariable(enricher: Enricher): EnricherMockValidationError =
      EnricherMockValidationError(
        typ = "MissingEnricherOutputVariable",
        message =
          s"Enricher output variable '${enricher.output}' is missing in variable types for node '${enricher.id}'",
        description = "Enricher output variable must be present in variable types to validate the mock expression",
        details = None
      )

  }

}
