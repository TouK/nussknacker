package pl.touk.nussknacker.engine.standalone.utils.service

import pl.touk.nussknacker.engine.api.{JobData, Service}
import pl.touk.nussknacker.engine.standalone.api.{StandaloneContext, StandaloneContextLifecycle}
import pl.touk.nussknacker.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with StandaloneContextLifecycle with WithEspTimers { self: Service =>

  var context: StandaloneContext = _

  override def open(jobData: JobData, runtimeContext: StandaloneContext): Unit = {
    self.open(jobData)
    context = runtimeContext
  }

}
