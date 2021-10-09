package pl.touk.nussknacker.engine.standalone.deployment

import pl.touk.nussknacker.engine.standalone.StandaloneScenarioEngine

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[StandaloneScenarioEngine.StandaloneScenarioInterpreter]

}
