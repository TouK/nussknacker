package pl.touk.nussknacker.engine.standalone.utils.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContext
import pl.touk.nussknacker.engine.util.service.EspTimer

trait WithEspTimers {

  def context: StandaloneContext

  protected def instantTimerWindowInSeconds: Long

  def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = context.espTimer(instantTimerWindowInSeconds, tags, name)

}
