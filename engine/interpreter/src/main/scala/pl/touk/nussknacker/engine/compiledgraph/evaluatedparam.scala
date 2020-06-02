package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionTypingInfo, TypedExpression, TypedValue}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object evaluatedparam {

  case class TypedParameter(name: String, typedValue: TypedValue)

  case class Parameter(typedExpression: TypedExpression, parameterDefinition: definition.Parameter) {

    def name: String = parameterDefinition.name

    def typingInfo: ExpressionTypingInfo = typedExpression.typingInfo

    def returnType: TypingResult = typedExpression.returnType

    def expression: Expression = typedExpression.expression

    def shouldBeWrappedWithScalaOption: Boolean = parameterDefinition.scalaOptionParameter

    def shouldBeWrappedWithJavaOptional: Boolean = parameterDefinition.javaOptionalParameter
  }

}
