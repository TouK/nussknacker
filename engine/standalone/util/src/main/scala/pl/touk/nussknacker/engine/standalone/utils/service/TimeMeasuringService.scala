package pl.touk.nussknacker.engine.standalone.utils.service

import pl.touk.nussknacker.engine.api.{JobData, Service}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{RuntimeContext, RuntimeContextLifecycle}
import pl.touk.nussknacker.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with RuntimeContextLifecycle with WithEspTimers { self: Service =>

  var context: RuntimeContext = _

  override def open(jobData: JobData, runtimeContext: RuntimeContext): Unit = {
    self.open(jobData)
    context = runtimeContext
  }

}
