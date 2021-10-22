package pl.touk.nussknacker.engine.baseengine.api.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.service.EspTimer

trait MetricsProvider {

  def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer

  def close(processId: String): Unit

}
