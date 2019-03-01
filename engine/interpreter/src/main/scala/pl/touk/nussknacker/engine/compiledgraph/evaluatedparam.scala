package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compiledgraph.expression.Expression

object evaluatedparam {

  case class Parameter(name: String, expression: Expression, returnType: TypingResult)

}
