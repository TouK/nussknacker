package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

sealed trait DefinedParameter

case class DefinedLazyParameter(returnType: TypingResult) extends DefinedParameter
case class DefinedEagerParameter(value: Any) extends DefinedParameter
case object FailedToDefineParameter extends DefinedParameter

