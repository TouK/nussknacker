package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition.{Parameter, Validator}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}

object CompiledParameter {

  def apply(
      typedExpression: TypedExpression,
      parameterDefinition: Parameter,
  ): CompiledParameter =
    apply(typedExpression, parameterDefinition, Nil)

  def apply(
      typedExpression: TypedExpression,
      parameterDefinition: Parameter,
      validators: List[Validator],
  ): CompiledParameter = {
    CompiledParameter(
      parameterDefinition.name,
      typedExpression.expression,
      parameterDefinition.scalaOptionParameter,
      parameterDefinition.javaOptionalParameter,
      typedExpression.typingInfo,
      validators,
    )
  }

}

final case class CompiledParameter(
    override val name: ParameterName,
    override val expression: CompiledExpression,
    override val shouldBeWrappedWithScalaOption: Boolean,
    override val shouldBeWrappedWithJavaOptional: Boolean,
    typingInfo: ExpressionTypingInfo,
    validators: List[Validator] = Nil,
) extends BaseCompiledParameter

trait BaseCompiledParameter {
  def name: ParameterName
  def expression: CompiledExpression
  def shouldBeWrappedWithScalaOption: Boolean
  def shouldBeWrappedWithJavaOptional: Boolean
}
