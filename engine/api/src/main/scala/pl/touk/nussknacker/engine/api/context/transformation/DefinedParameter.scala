package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}

sealed trait DefinedParameter {
  def returnType: TypingResult
}

case class DefinedLazyParameter(returnType: TypingResult) extends DefinedParameter
case class DefinedEagerParameter(value: Any, returnType: TypingResult) extends DefinedParameter
case object FailedToDefineParameter extends DefinedParameter {
  override def returnType: TypingResult = Unknown
}

