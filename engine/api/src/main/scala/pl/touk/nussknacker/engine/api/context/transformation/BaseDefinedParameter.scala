package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

sealed trait BaseDefinedParameter {
  def returnType: TypingResult
}

sealed trait DefinedSingleParameter extends BaseDefinedParameter

trait ValidDefinedSingleParameter { self: DefinedSingleParameter =>

  def expression: TypedExpression

  final override def returnType: TypingResult = expression.returnType

}

sealed trait DefinedBranchParameter extends BaseDefinedParameter {

  def expressionByBranchId: Map[String, TypedExpression]

  final override def returnType: TypingResult = Typed(expressionByBranchId.values.map(_.returnType).toSet)

}

case class DefinedLazyParameter(expression: TypedExpression) extends DefinedSingleParameter with ValidDefinedSingleParameter
case class DefinedEagerParameter(value: Any, expression: TypedExpression) extends DefinedSingleParameter with ValidDefinedSingleParameter

case class DefinedLazyBranchParameter(expressionByBranchId: Map[String, TypedExpression]) extends DefinedBranchParameter
case class DefinedEagerBranchParameter(value: Map[String, Any], expressionByBranchId: Map[String, TypedExpression]) extends DefinedBranchParameter

//TODO: and for branch parameters??
case object FailedToDefineParameter extends DefinedSingleParameter {
  override val returnType: TypingResult = Unknown
}

