package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.test.testcase.Assertion
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.testcase.{AssertionIndex, AssertionValidationError}
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.AssertionExpressionCompilationError
import pl.touk.nussknacker.ui.process.test.testcase.AssertionsCompiler

class AssertionValidator(
    assertionsCompiler: AssertionsCompiler
) {

  def validate(
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
