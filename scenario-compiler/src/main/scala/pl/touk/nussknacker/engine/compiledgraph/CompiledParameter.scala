package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}

object CompiledParameter {

  def apply(
      typedExpression: TypedExpression,
      parameterDefinition: Parameter
  ): CompiledParameter = {
    new CompiledParameter(
      parameterDefinition.name,
      typedExpression.expression,
      parameterDefinition.scalaOptionParameter,
      parameterDefinition.javaOptionalParameter,
      typedExpression.typingInfo
    )
  }

}

final class CompiledParameter(
    val name: ParameterName,
    val expression: CompiledExpression,
    val shouldBeWrappedWithScalaOption: Boolean,
    val shouldBeWrappedWithJavaOptional: Boolean,
    val typingInfo: ExpressionTypingInfo
)
