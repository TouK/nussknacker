package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter


object exceptionhandler {

  @JsonCodec case class ExceptionHandlerRef(parameters: List[Parameter])

}
