package pl.touk.nussknacker.engine.standalone.utils.service

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{JobData, Service}
import pl.touk.nussknacker.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle}
import pl.touk.nussknacker.engine.util.service.{EspTimer, GenericTimeMeasuringService}

trait TimeMeasuringService extends GenericTimeMeasuringService with StandaloneContextLifecycle with WithEspTimers { self: Service =>

  var context: StandaloneContext = _

  override def open(jobData: JobData, runtimeContext: StandaloneContext): Unit = {
    self.open(jobData)
    context = runtimeContext
  }

  override def espTimer(tags: Map[String, String], name: String): EspTimer = espTimer(tags, NonEmptyList.of(name))
}
