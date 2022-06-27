package pl.touk.nussknacker.engine.requestresponse.deployment

import pl.touk.nussknacker.engine.requestresponse.RequestResponseRequestHandler

trait ProcessInterpreters {
  def getInterpreterHandlerByPath(path: String): Option[RequestResponseRequestHandler]
}
