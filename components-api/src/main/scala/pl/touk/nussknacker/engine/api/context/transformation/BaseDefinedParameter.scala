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

  def expressionByBranchId: Map[String, TypingResult]

  final override def returnType: TypingResult =
    Typed.fromIterableOrUnknownIfEmpty(expressionByBranchId.values.map(_.withoutValue))

}

case class DefinedLazyParameter(returnType: TypingResult)
    extends DefinedSingleParameter
    with ValidDefinedSingleParameter

case class DefinedEagerParameter(value: Any, returnType: TypingResult)
    extends DefinedSingleParameter
    with ValidDefinedSingleParameter

case class DefinedLazyBranchParameter(expressionByBranchId: Map[String, TypingResult]) extends DefinedBranchParameter
case class DefinedEagerBranchParameter(value: Map[String, Any], expressionByBranchId: Map[String, TypingResult])
    extends DefinedBranchParameter

//TODO: and for branch parameters??
case object FailedToDefineParameter extends DefinedSingleParameter {
  override val returnType: TypingResult = Unknown
}
