package pl.touk.nussknacker.engine.standalone.utils.service

import pl.touk.nussknacker.engine.api.{JobData, Service}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, RuntimeContextLifecycle}
import pl.touk.nussknacker.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with RuntimeContextLifecycle with WithEspTimers { self: Service =>

  var context: EngineRuntimeContext = _

  override def open(jobData: JobData, runtimeContext: EngineRuntimeContext): Unit = {
    self.open(jobData)
    context = runtimeContext
  }

}
