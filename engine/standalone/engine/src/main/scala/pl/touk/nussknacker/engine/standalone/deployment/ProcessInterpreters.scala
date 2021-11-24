package pl.touk.nussknacker.engine.standalone.deployment

import pl.touk.nussknacker.engine.standalone.StandaloneScenarioEngine

import scala.concurrent.Future

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[StandaloneScenarioEngine.StandaloneScenarioInterpreter[Future]]

}
