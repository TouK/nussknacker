package pl.touk.nussknacker.engine.speltemplate

import pl.touk.nussknacker.engine.graph.expression.Expression
import scala.language.implicitConversions
object Implicits {
  implicit def asSpelTemplateExpression(expression: String): Expression = Expression.spelTemplate(expression)
}
