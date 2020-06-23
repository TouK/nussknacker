package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

sealed trait DefinedParameter {
  def returnType: TypingResult
}

sealed trait DefinedSingleParameter extends DefinedParameter

sealed trait DefinedBranchParameter extends DefinedParameter {

  override def returnType: TypingResult = Typed(detailedReturnType.values.toSet)

  def detailedReturnType: Map[String, TypingResult]

}

case class DefinedLazyParameter(returnType: TypingResult) extends DefinedSingleParameter
case class DefinedEagerParameter(value: Any, returnType: TypingResult) extends DefinedSingleParameter

case class DefinedLazyBranchParameter(detailedReturnType: Map[String, TypingResult]  ) extends DefinedBranchParameter
case class DefinedEagerBranchParameter(value: Map[String, Any], detailedReturnType: Map[String, TypingResult]  ) extends DefinedBranchParameter

//TODO: and for branch parameters??
case object FailedToDefineParameter extends DefinedSingleParameter {
  override val returnType: TypingResult = Unknown
}

