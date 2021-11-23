package pl.touk.nussknacker.engine.requestresponse.deployment

import pl.touk.nussknacker.engine.requestresponse.StandaloneScenarioEngine

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[StandaloneScenarioEngine.StandaloneScenarioInterpreter]

}
