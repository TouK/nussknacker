package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter


object exceptionhandler {

  @JsonCodec case class ExceptionHandlerRef(parameters: List[Parameter])

}
