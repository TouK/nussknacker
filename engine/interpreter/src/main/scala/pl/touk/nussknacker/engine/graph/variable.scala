package pl.touk.nussknacker.engine.graph

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

object variable {

  @JsonCodec case class Field(name: String, expression: Expression)

}
