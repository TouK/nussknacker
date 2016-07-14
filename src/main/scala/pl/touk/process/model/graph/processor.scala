package pl.touk.process.model.graph

import pl.touk.process.model.graph.expression.Expression

object processor {

  case class Parameter(name: String, expression: Expression)

  case class ProcessorRef(parameters: List[Parameter], id: String)


}
