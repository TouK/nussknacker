package pl.touk.esp.engine.flink.util.service

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.MetricGroup
import pl.touk.esp.engine.flink.api.RuntimeContextLifecycle
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait TimeMeasuringService extends RuntimeContextLifecycle {

  @transient var metrics : Map[String, EspTimer] = _

  @transient var metricGroupTimer: MetricGroup = _

  @transient var metricGroupInstant: MetricGroup = _


  override def open(runtimeContext: RuntimeContext) = {
    super.open(runtimeContext)
    metricGroupTimer = runtimeContext.getMetricGroup.addGroup("serviceTimes").addGroup(serviceName)
    metricGroupInstant = runtimeContext.getMetricGroup.addGroup("serviceInstant").addGroup(serviceName)
    metrics = Map()
  }

  protected def measuring[T](action: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    val start = System.nanoTime()
    action.onComplete { result =>
      detectMeterName(result).foreach { meterName =>
        getOrCreateTimer(meterName).update(start)
      }
    }
    action
  }

  protected def serviceName: String

  protected def instantTimerWindowInSeconds : Long = 20

  protected def detectMeterName(result: Try[Any]) : Option[String] = result match {
    case Success(_) => Some("OK")
    case Failure(_) => Some("FAIL")
  }

  private def getOrCreateTimer(name: String) : EspTimer = {
    if (!metrics.contains(name)) {
      val meter = new InstantRateMeter
      val histogram = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
      metrics += (name -> EspTimer(
        metricGroupInstant.gauge[Double, InstantRateMeter](name, meter),
        metricGroupTimer.histogram(name, histogram)))
    }
    metrics(name)
  }

}

private[service] case class EspTimer(rateMeter: InstantRateMeter, histogram: DropwizardHistogramWrapper) {

  def update(nanoTimeStart: Long) = {
    val delta = System.nanoTime() - nanoTimeStart
    rateMeter.mark()
    histogram.update(delta)
  }

}
