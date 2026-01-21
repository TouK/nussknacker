package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.functor._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.test.testcase.{Assertion, EnricherMock, TestCase}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{ModelData, ScenarioCompilationDependencies}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.testcase.{AssertionIndex, AssertionValidationError, EnricherMockValidationError, NodeTestCaseValidationErrors, NodeTestCasesValidationErrors, ScenarioTestCasesValidationErrors}
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.AssertionExpressionCompilationError
import pl.touk.nussknacker.ui.process.test.testcase.AssertionsCompiler

class TestCaseValidator(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer,
    assertionsCompiler: AssertionsCompiler
) {

  import TestCaseValidator._

  def validateScenarioTestCases(
      nodes: List[NodeData],
      nodesTyping: Map[String, TestCaseValidator.NodeTyping],
      testCases: List[TestCase],
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ScenarioTestCasesValidationErrors = {
    val nodesById = nodes.map(n => NodeId(n.id) -> n).toMap
    val nodeTestCasesErrors = nodesById.flatMap { case (nodeId, node) =>
      val nodeTestCases = testCases.flatMap { testCase =>
        val hasMock       = testCase.mocks.contains(nodeId)
        val hasAssertions = testCase.assertions.get(nodeId).exists(_.nonEmpty)

        if (hasMock || hasAssertions) {
          Some(
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
        val nodeTyping = nodesTyping.getOrElse(nodeId.id, NodeTyping.empty)
        val errors = validateNodeTestCases(
          node,
          nodeTestCases,
          nodeTyping
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
    val enricherMockErrors = validateEnricherMock(
      nodeData,
      nodeTestCase.enricherMock,
      nodeTyping
    )
    val assertionsErrors = validateAssertions(
      NodeId(nodeData.id),
      nodeTestCase.assertions,
      nodeTyping.inputVariables,
      scenarioCompilationDependencies.jobData
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
      nodeTyping: NodeTyping,
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Option[NonEmptyList[EnricherMockValidationError]] = {
    enricherMock match {
      case Some(mock) =>
        nodeData match {
          case enricher: Enricher =>
            validateEnricherMock(enricher, mock.expression, nodeTyping) match {
              case Invalid(errors) => Some(errors)
              case Valid(_)        => None
            }
          case _ =>
            Some(NonEmptyList.one(errors.enricherMockForNonEnricherNode(nodeData)))
        }
      case None => None
    }
  }

  private def validateEnricherMock(
      enricher: Enricher,
      mockExpression: Expression,
      nodeTyping: NodeTyping,
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidatedNel[EnricherMockValidationError, Unit] = {
    val expectedOutputVariableType =
      determineEnricherOutputVariableType(enricher, nodeTyping.outputVariables)
    validateEnricherMockMatchesOutputType(
      enricher,
      mockExpression,
      nodeTyping.inputVariables,
      expectedOutputVariableType
    )
  }

  private def determineEnricherOutputVariableType(
      enricher: Enricher,
      outputVariableTypes: Map[String, TypingResult],
  ): ValidatedNel[EnricherMockValidationError, TypingResult] = {
    Validated
      .fromOption(
        outputVariableTypes.get(enricher.output),
        errors.missingEnricherOutputVariable(enricher)
      )
      .toValidatedNel
  }

  private def validateEnricherMockMatchesOutputType(
      enricher: Enricher,
      mockExpression: Expression,
      inputVariableTypes: Map[String, TypingResult],
      expectedOutputVariableType: ValidatedNel[EnricherMockValidationError, TypingResult],
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidatedNel[EnricherMockValidationError, Unit] = {
    expectedOutputVariableType
      .andThen { typ =>
        implicit val nodeId: NodeId = NodeId(enricher.id)
        val validationContextWithGlobalVariablesOnly =
          globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(
            scenarioCompilationDependencies.jobData
          )
        val inputContext = validationContextWithGlobalVariablesOnly.copy(localVariables = inputVariableTypes)

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
      inputVariableTypes: Map[String, TypingResult],
      jobData: JobData
  ): Option[Map[AssertionIndex, NonEmptyList[AssertionValidationError]]] = {
    if (assertions.isEmpty) {
      None
    } else {
      val compilationResults = assertionsCompiler.compileForNode(nodeId, assertions, inputVariableTypes, jobData)
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
      expressionCompiler,
      globalVariablesPreparer,
      new AssertionsCompiler(
        expressionCompiler,
        globalVariablesPreparer
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
