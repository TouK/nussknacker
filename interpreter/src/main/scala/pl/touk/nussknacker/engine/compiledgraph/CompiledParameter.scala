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

final case class CompiledParameter(
    override val name: String,
    override val expression: CompiledExpression,
    override val shouldBeWrappedWithScalaOption: Boolean,
    override val shouldBeWrappedWithJavaOptional: Boolean,
    typingInfo: ExpressionTypingInfo
) extends BaseCompiledParameter

trait BaseCompiledParameter {
  def name: String
  def expression: CompiledExpression
  def shouldBeWrappedWithScalaOption: Boolean
  def shouldBeWrappedWithJavaOptional: Boolean
}
