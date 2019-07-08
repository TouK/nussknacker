package pl.touk.nussknacker.engine.standalone.deployment

import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter

trait ProcessInterpreters {

  def getInterpreterByPath(path: String): Option[StandaloneProcessInterpreter]

}
