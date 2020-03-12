package pl.touk.nussknacker.engine.standalone.utils.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter
import pl.touk.nussknacker.engine.util.service.EspTimer

trait MetricsProvider {

  def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer

  def close(processId: String): Unit

}

//mainly for tests, both unit and tests from Nussknacker UI
object NoOpMetricsProvider extends MetricsProvider {

  override def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer =
    EspTimer(new GenericInstantRateMeter {}, _ => ())

  override def close(processId: String): Unit = {}
}