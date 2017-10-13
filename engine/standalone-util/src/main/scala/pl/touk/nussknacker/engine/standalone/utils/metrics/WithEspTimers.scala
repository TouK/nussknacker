package pl.touk.nussknacker.engine.standalone.utils.metrics

import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, MetricRegistry, SlidingTimeWindowReservoir}
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContext
import pl.touk.nussknacker.engine.util.service.EspTimer

trait WithEspTimers {

  def context: StandaloneContext

  protected def instantTimerWindowInSeconds: Long

  def metricName(timerName: String): List[String] = {
    List(timerName)
  }

  def espTimer(name: String) = {
    val histogram = new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS))
    val registered = context.register(MetricRegistry.name("times", metricName(name): _*), histogram)
    val meter = context.register(MetricRegistry.name("instant", metricName(name): _*), new InstantRateMeter)
    EspTimer(meter, registered.update)
  }

}




