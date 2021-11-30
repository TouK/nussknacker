package pl.touk.nussknacker.engine.requestresponse.deployment

import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[InterpreterType]

}
