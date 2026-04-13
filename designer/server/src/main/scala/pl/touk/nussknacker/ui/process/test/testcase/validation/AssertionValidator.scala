package pl.touk.nussknacker.ui.process.test.testcase.validation

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.api.{JobData, NodeId, NodeName}
import pl.touk.nussknacker.engine.test.testcase.Assertion
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.testcase.{AssertionIndex, AssertionValidationError}
import pl.touk.nussknacker.ui.process.test.testcase
import pl.touk.nussknacker.ui.process.test.testcase.{AssertionCompilationError, AssertionsCompiler}
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.ExpressionAssertionCompilationError

private class AssertionValidator(
    assertionsCompiler: AssertionsCompiler
) {

  def validateForNode(
      assertions: List[Assertion],
      nodeTyping: testcase.NodeTyping,
      jobData: JobData
  )(
      implicit nodeId: NodeId,
      nodeName: NodeName
  ): Option[Map[AssertionIndex, NonEmptyList[AssertionValidationError]]] = {
    val compilationResults =
      assertionsCompiler.compileForNode(assertions, nodeTyping, jobData)
    val errorsMap = compilationResults.zipWithIndex.collect { case (Invalid(errors), index) =>
      index -> convertToAssertionErrors(errors)
    }.toMap
    Some(errorsMap).filter(_.nonEmpty)
  }

  private def convertToAssertionErrors(
      assertionErrors: NonEmptyList[AssertionCompilationError]
  ): NonEmptyList[AssertionValidationError] = {
    assertionErrors.flatMap {
      case ExpressionAssertionCompilationError(errors, _, _, _) =>
        errors.map { error =>
          val prettyError = PrettyValidationErrors.formatErrorMessage(error)
          AssertionValidationError(
            typ = prettyError.typ,
            message = prettyError.message,
            description = prettyError.description,
            details = prettyError.details,
            fieldName = None,
          )
        }
      case AssertionCompilationError.PredicateAssertionCompilationError(errors, _, field, _, _) =>
        errors.map { error =>
          val prettyError = PrettyValidationErrors.formatErrorMessage(error)
          AssertionValidationError(
            typ = prettyError.typ,
            message = prettyError.message,
            description = prettyError.description,
            details = prettyError.details,
            fieldName = Some(field.entryName),
          )
        }
    }
  }

}
