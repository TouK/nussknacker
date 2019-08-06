package pl.touk.nussknacker.engine.standalone.utils.metrics

import pl.touk.nussknacker.engine.standalone.utils.StandaloneContext
import pl.touk.nussknacker.engine.util.service.EspTimer

trait WithEspTimers {

  def context: StandaloneContext

  protected def instantTimerWindowInSeconds: Long

  def espTimer(name: String*): EspTimer = context.espTimer(instantTimerWindowInSeconds, name:_*)

}




