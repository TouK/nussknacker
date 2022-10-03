package pl.touk.nussknacker.ui.metrics

import io.dropwizard.metrics5.MetricRegistry

import scala.concurrent.{ExecutionContext, Future}

object TimeMeasuring extends TimeMeasuring

trait TimeMeasuring {

  def measureTime[T](name: String, metricRegistry: MetricRegistry)(action: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val ctx = metricRegistry.timer(name).time()
    val toReturn = action
    toReturn.onComplete(_ => ctx.stop())
    toReturn
  }

}
