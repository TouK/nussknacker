package pl.touk.nussknacker.engine.requestresponse.deployment

import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[RequestResponseEngine.RequestResponseScenarioInterpreter]

}
