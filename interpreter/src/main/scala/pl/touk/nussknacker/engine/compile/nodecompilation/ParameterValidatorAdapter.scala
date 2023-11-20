package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression

object ParameterValidatorAdapter {

  def validate(
      expression: Expression,
      typingResult: Option[TypingResult],
      fieldName: String,
      validator: ParameterValidator
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    validator.isValid(fieldName, expression, extractValue(typingResult), None).toValidatedNel
  }

  private def extractValue(typingResult: Option[TypingResult]): Any = typingResult match {
    case Some(value) =>
      value.valueOpt match {
        case Some(value) => value
        case None        => null
      }
    case None => null
  }

}
