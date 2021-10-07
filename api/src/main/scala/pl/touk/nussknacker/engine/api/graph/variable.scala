package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.expression.Expression

object variable {

  @JsonCodec case class Field(name: String, expression: Expression)

}
