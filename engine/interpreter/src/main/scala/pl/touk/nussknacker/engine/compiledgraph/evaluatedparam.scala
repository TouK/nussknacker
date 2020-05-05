package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionTypingInfo, TypedValue}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object evaluatedparam {

  case class TypedParameter(name: String, typedValue: TypedValue)

  case class Parameter(name: String, expression: Expression, returnType: TypingResult,
                       shouldBeWrappedWithScalaOption: Boolean, shouldBeWrappedWithJavaOptional: Boolean,
                       typingInfo: ExpressionTypingInfo)

}
