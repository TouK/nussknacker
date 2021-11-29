package pl.touk.nussknacker.engine.requestresponse.utils.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.MetricIdentifier
import pl.touk.nussknacker.engine.util.service.EspTimer

trait WithEspTimers {

  def context: EngineRuntimeContext

  protected def instantTimerWindowInSeconds: Long

  def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = context.metricsProvider.espTimer(MetricIdentifier(name, tags), instantTimerWindowInSeconds)

}
