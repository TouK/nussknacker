package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

sealed trait BaseDefinedParameter {
  def returnType: TypingResult
}

sealed trait DefinedSingleParameter extends BaseDefinedParameter

trait ValidDefinedSingleParameter { self: DefinedSingleParameter =>

  def returnType: TypingResult

}

sealed trait DefinedBranchParameter extends BaseDefinedParameter {

  def typeByBranchId: Map[String, TypingResult]

  final override def returnType: TypingResult = Typed(typeByBranchId.values.toSet)

}

case class DefinedLazyParameter(returnType: TypingResult)
    extends DefinedSingleParameter
    with ValidDefinedSingleParameter

case class DefinedEagerParameter(value: Any, returnType: TypingResult)
    extends DefinedSingleParameter
    with ValidDefinedSingleParameter

case class DefinedLazyBranchParameter(typeByBranchId: Map[String, TypingResult]) extends DefinedBranchParameter
case class DefinedEagerBranchParameter(value: Map[String, Any], typeByBranchId: Map[String, TypingResult])
    extends DefinedBranchParameter

//TODO: and for branch parameters??
case object FailedToDefineParameter extends DefinedSingleParameter {
  override val returnType: TypingResult = Unknown
}
