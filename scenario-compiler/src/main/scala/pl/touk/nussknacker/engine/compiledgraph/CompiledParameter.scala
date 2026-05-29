package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression

object CompiledParameter {

  def apply(
      typedExpression: TypedExpression,
      parameterDefinition: Parameter,
      validators: List[Validator] = Nil,
  ): CompiledParameter =
    CompiledParameter(
      parameterDefinition.name,
      typedExpression.expression,
      parameterDefinition.scalaOptionParameter,
      parameterDefinition.javaOptionalParameter,
      typedExpression.typingInfo,
      validators,
    )

}

final case class CompiledParameter(
    override val name: ParameterName,
    override val expression: CompiledExpression,
    override val shouldBeWrappedWithScalaOption: Boolean,
    override val shouldBeWrappedWithJavaOptional: Boolean,
    typingInfo: ExpressionTypingInfo,
    validators: List[Validator],
) extends BaseCompiledParameter {

  val expressionForValidation: Expression =
    Expression(expression.language, expression.original)

  lazy val runtimeValidators: List[RuntimeValidator] =
    validators.collect { case v: RuntimeValidator => v }

}

trait BaseCompiledParameter {
  def name: ParameterName
  def expression: CompiledExpression
  def shouldBeWrappedWithScalaOption: Boolean
  def shouldBeWrappedWithJavaOptional: Boolean
}
