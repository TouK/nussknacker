package pl.touk.nussknacker.engine.standalone.utils.service

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, MetricRegistry, SlidingTimeWindowReservoir}
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle}
import pl.touk.nussknacker.engine.standalone.utils.metrics.{InstantRateMeter, WithEspTimers}
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with StandaloneContextLifecycle with WithEspTimers {

  var context: StandaloneContext = _

  override def open(runtimeContext: StandaloneContext) = {
    context = runtimeContext
  }

}
