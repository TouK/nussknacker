package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.expression.parse.CompiledExpression

object variable {

  case class Field(name: String, expression: CompiledExpression)

}
