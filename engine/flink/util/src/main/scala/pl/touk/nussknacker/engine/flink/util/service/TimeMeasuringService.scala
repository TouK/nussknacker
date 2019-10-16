package pl.touk.nussknacker.engine.flink.util.service

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.MetricGroup
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeter
import pl.touk.nussknacker.engine.util.metrics.RateMeter
import pl.touk.nussknacker.engine.util.service.{EspTimer, GenericTimeMeasuringService}

trait TimeMeasuringService extends GenericTimeMeasuringService with RuntimeContextLifecycle with LazyLogging {

  @transient var metricGroupTimer: MetricGroup = _

  @transient var metricGroupInstant: MetricGroup = _

  private val dummyTimer = EspTimer(new RateMeter {
    override def mark(): Unit = {}
  }, _ => ())

  override def open(runtimeContext: RuntimeContext): Unit = {
    super.open(runtimeContext)
    metricGroupTimer = runtimeContext.getMetricGroup.addGroup("serviceTimes").addGroup(serviceName)
    metricGroupInstant = runtimeContext.getMetricGroup.addGroup("serviceInstant").addGroup(serviceName)
  }

  override def espTimer(name: String): EspTimer = {
    //TODO: so far in ServiceQuery we don't do open(...) because there's no RuntimeContext
    //we should make it nicer than below, but it's still better than throwing NullPointerException
    if (metricGroupInstant == null) {
      logger.info("open not called on TimeMeasuringService - is it ServiceQuery? Using dummy timer")
      dummyTimer
    } else {
      val meter = metricGroupInstant.gauge[Double, InstantRateMeter](name, new InstantRateMeter)
      val histogram = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
      val registered = metricGroupTimer.histogram(name, histogram)
      EspTimer(meter, registered.update)
    }
  }

}
