package pl.touk.nussknacker.engine.standalone.utils.service

import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.standalone.utils.metrics.WithEspTimers
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle}
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with StandaloneContextLifecycle with WithEspTimers { self: Service =>

  var context: StandaloneContext = _

  override def open(runtimeContext: StandaloneContext) = {
    self.open()
    context = runtimeContext
  }

  override def metricName(timerName: String) = {
    List(serviceName, timerName)
  }

}
