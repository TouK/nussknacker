package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated._
import cats.syntax.functor._
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.test.testcase.EnricherMock
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.testcase.EnricherMockValidationError
import pl.touk.nussknacker.ui.process.test.testcase

private class EnricherMockValidator(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer,
) {

  import EnricherMockValidator._

  def validateForNode(
      nodeData: NodeData,
      enricherMock: Option[EnricherMock],
      nodeTyping: testcase.NodeTyping,
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): Option[NonEmptyList[EnricherMockValidationError]] = {
    (enricherMock, nodeData) match {
      case (Some(EnricherMock.EnabledExpression(mockExpression)), enricher: Enricher) =>
        validateEnricherMockType(enricher, mockExpression, nodeTyping) match {
          case Invalid(errors) => Some(errors)
          case Valid(_)        => None
        }
      case (_, _: Enricher) | (None, _) =>
        None
      case (Some(_), _) =>
        Some(NonEmptyList.one(errors.enricherMockForNonEnricherNode(nodeData)))
    }
  }

  private def validateEnricherMockType(
      enricher: Enricher,
      mockExpression: Expression,
      nodeTyping: testcase.NodeTyping,
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
      outputVariableTypes: Option[Map[String, TypingResult]],
  ): ValidatedNel[EnricherMockValidationError, TypingResult] = {
    Validated
      .fromOption(
        outputVariableTypes.flatMap(_.get(enricher.output)),
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
        implicit val nodeId: NodeId = enricher.id
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

}

private object EnricherMockValidator {

  private object errors {

    def enricherMockForNonEnricherNode(nodeData: NodeData): EnricherMockValidationError =
      EnricherMockValidationError(
        typ = "MockForNonEnricherNode",
        message = s"Mock configured for non-enricher node '${nodeData.name.value}'",
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
