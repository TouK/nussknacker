package pl.touk.nussknacker.engine.standalone.utils.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.RuntimeContext
import pl.touk.nussknacker.engine.util.service.EspTimer

trait WithEspTimers {

  def context: RuntimeContext

  protected def instantTimerWindowInSeconds: Long

  def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = context.espTimer(instantTimerWindowInSeconds, tags, name)

}
