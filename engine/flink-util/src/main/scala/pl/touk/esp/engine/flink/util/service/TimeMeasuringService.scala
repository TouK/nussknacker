package pl.touk.esp.engine.flink.util.service

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.MetricGroup
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter
import pl.touk.esp.engine.util.service.{EspTimer, GenericTimeMeasuringService}

trait TimeMeasuringService extends GenericTimeMeasuringService with RuntimeContextLifecycle {

  @transient var metricGroupTimer: MetricGroup = _

  @transient var metricGroupInstant: MetricGroup = _

  override def open(runtimeContext: RuntimeContext) = {
    super.open(runtimeContext)
    metricGroupTimer = runtimeContext.getMetricGroup.addGroup("serviceTimes").addGroup(serviceName)
    metricGroupInstant = runtimeContext.getMetricGroup.addGroup("serviceInstant").addGroup(serviceName)
  }

  override def espTimer(name: String) = {
    val meter = metricGroupInstant.gauge[Double, InstantRateMeter](name, new InstantRateMeter)
    val histogram = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
    val registered = metricGroupTimer.histogram(name, histogram)
    EspTimer(meter, registered.update)
  }

}
