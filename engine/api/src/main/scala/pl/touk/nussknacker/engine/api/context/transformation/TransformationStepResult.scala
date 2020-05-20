package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter

sealed trait TransformationStepResult {
  def errors: List[ProcessCompilationError]
}

case class NextParameters(parameters: List[Parameter], errors: List[ProcessCompilationError] = Nil) extends TransformationStepResult
case class FinalResults(finalContext: ValidationContext, errors: List[ProcessCompilationError] = Nil) extends TransformationStepResult

