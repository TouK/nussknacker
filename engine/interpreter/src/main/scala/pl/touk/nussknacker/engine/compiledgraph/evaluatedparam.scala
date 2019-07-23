package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compiledgraph.expression.Expression

object evaluatedparam {

  case class TypedParameter(name: String, typedValue: TypedValue)

  sealed trait TypedValue

  case class TypedExpression(expression: Expression, returnType: TypingResult) extends TypedValue

  case class TypedExpressionMap(valueByKey: Map[String, TypedExpression]) extends TypedValue

  case class Parameter(name: String, expression: Expression, returnType: TypingResult)

}
