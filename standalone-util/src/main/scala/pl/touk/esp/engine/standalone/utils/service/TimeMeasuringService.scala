package pl.touk.esp.engine.standalone.utils.service

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, MetricRegistry, SlidingTimeWindowReservoir}
import pl.touk.esp.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle}
import pl.touk.esp.engine.standalone.utils.metrics.{InstantRateMeter, WithEspTimers}
import pl.touk.esp.engine.util.service.GenericTimeMeasuringService

trait TimeMeasuringService extends GenericTimeMeasuringService with StandaloneContextLifecycle with WithEspTimers {

  var context: StandaloneContext = _

  override def open(runtimeContext: StandaloneContext) = {
    context = runtimeContext
  }

}
