package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.{ExpressionTypingInfo, TypedExpression}
import pl.touk.nussknacker.engine.api.expression.{Expression => CompiledExpression}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object CompiledParameter {

  def apply(
      typedExpression: TypedExpression,
      parameterDefinition: Parameter
  ): CompiledParameter = {
    CompiledParameter(
      parameterDefinition.name,
      typedExpression.expression,
      parameterDefinition.scalaOptionParameter,
      parameterDefinition.javaOptionalParameter,
      typedExpression.typingInfo
    )
  }

}

case class CompiledParameter(
    name: String,
    expression: CompiledExpression,
    shouldBeWrappedWithScalaOption: Boolean,
    shouldBeWrappedWithJavaOptional: Boolean,
    typingInfo: ExpressionTypingInfo
)
