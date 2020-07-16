package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

sealed trait BaseDefinedParameter {
  def returnType: TypingResult
}

sealed trait DefinedSingleParameter extends BaseDefinedParameter

sealed trait DefinedBranchParameter extends BaseDefinedParameter {

  override def returnType: TypingResult = Typed(returnTypeByBranchId.values.toSet)

  def returnTypeByBranchId: Map[String, TypingResult]

}

case class DefinedLazyParameter(returnType: TypingResult) extends DefinedSingleParameter
case class DefinedEagerParameter(value: Any, returnType: TypingResult) extends DefinedSingleParameter

case class DefinedLazyBranchParameter(returnTypeByBranchId: Map[String, TypingResult]  ) extends DefinedBranchParameter
case class DefinedEagerBranchParameter(value: Map[String, Any], returnTypeByBranchId: Map[String, TypingResult]  ) extends DefinedBranchParameter

//TODO: and for branch parameters??
case object FailedToDefineParameter extends DefinedSingleParameter {
  override val returnType: TypingResult = Unknown
}

