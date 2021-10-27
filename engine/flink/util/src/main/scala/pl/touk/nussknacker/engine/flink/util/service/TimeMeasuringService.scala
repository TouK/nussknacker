package pl.touk.nussknacker.engine.flink.util.service

import cats.data.NonEmptyList
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeter, WithMetrics}
import pl.touk.nussknacker.engine.util.metrics.RateMeter
import pl.touk.nussknacker.engine.util.service.{EspTimer, GenericTimeMeasuringService}

import java.util.concurrent.TimeUnit

trait TimeMeasuringService extends GenericTimeMeasuringService with WithMetrics with LazyLogging {

  private val dummyTimer = EspTimer(new RateMeter {
    override def mark(): Unit = {}
  }, _ => ())

  override def espTimer(tags: Map[String, String], metricName: NonEmptyList[String]): EspTimer = {
    //TODO: so far in ServiceQuery we don't do open(...) because there's no RuntimeContext
    //we should make it nicer than below, but it's still better than throwing NullPointerException
    if (metricUtils == null) {
      logger.info("open not called on TimeMeasuringService - is it ServiceQuery? Using dummy timer")
      dummyTimer
    } else {
      // TODO: maybe we should also user here InstantRateMeterWithCount.register to have access also to counts?
      val meter = metricUtils.gauge[Double, InstantRateMeter](metricName :+ EspTimer.instantRateSuffix, tags, new InstantRateMeter)
      val histogram = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
      val registered = metricUtils.histogram(metricName :+ EspTimer.histogramSuffix, tags, histogram)
      EspTimer(meter, registered.update)
    }
  }

}
