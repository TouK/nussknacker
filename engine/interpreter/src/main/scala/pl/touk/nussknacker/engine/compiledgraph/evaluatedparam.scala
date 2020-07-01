package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionTypingInfo, TypedExpression, TypedValue}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object evaluatedparam {

  case class TypedParameter(name: String, typedValue: TypedValue)

  object Parameter {
    def apply(typedExpression: TypedExpression, parameterDefinition: definition.Parameter): Parameter = {
      Parameter(parameterDefinition.name, typedExpression.expression, typedExpression.returnType,
        parameterDefinition.scalaOptionParameter, parameterDefinition.javaOptionalParameter, typedExpression.typingInfo)
    }
  }

  case class Parameter(name: String, expression: Expression, returnType: TypingResult,
                       shouldBeWrappedWithScalaOption: Boolean, shouldBeWrappedWithJavaOptional: Boolean,
                       typingInfo: ExpressionTypingInfo)

}
