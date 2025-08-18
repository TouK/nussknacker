package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

class NullExpression(override val original: String, override val language: Language) extends CompiledExpression {

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = null.asInstanceOf[T]

}
