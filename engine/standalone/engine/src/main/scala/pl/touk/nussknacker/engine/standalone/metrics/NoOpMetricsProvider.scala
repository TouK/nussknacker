package pl.touk.nussknacker.engine.standalone.metrics

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.standalone.api.metrics.MetricsProvider
import pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter
import pl.touk.nussknacker.engine.util.service.EspTimer

//mainly for tests, both unit and tests from Nussknacker UI
object NoOpMetricsProvider extends MetricsProvider {

  override def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer =
    EspTimer(new GenericInstantRateMeter {}, _ => ())

  override def close(processId: String): Unit = {}
}
