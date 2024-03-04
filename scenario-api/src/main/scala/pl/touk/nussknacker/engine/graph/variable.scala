package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

object variable {

  @JsonCodec case class Field(name: String, expression: Expression)

}
