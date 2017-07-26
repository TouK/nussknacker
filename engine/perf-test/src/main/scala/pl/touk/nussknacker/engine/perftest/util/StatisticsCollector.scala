package pl.touk.nussknacker.engine.perftest.util

import akka.actor.{ActorSystem, Cancellable, Scheduler}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class StatisticsCollector[T: Ordering](scheduler: Scheduler,
                                       interval: FiniteDuration,
                                       id: String)
                                      (collect: => Future[T])
                                      (implicit ec: ExecutionContext) extends LazyLogging {

  @volatile private var histogram: Histogram[T] = Histogram.empty[T]

  def start(): Started = {
    val cancellable = scheduler.schedule(0 seconds, interval) {
      collect andThen {
        case Success(value) =>
          logger.debug(s"$id: fetched $value")
          histogram = histogram.withValue(value)
        case Failure(ex) =>
          logger.error("Error while collecting statistics. Histogram won't be complete", ex)
      }
    }
    new Started(cancellable)
  }

  class Started(cancellable: Cancellable) {

    def stop(): Stopped = {
      cancellable.cancel()
      new Stopped(histogram)
    }

  }

  class Stopped(val histogram: Histogram[T])

}

object StatisticsCollector {

  def apply[T: Ordering](system: ActorSystem,
                         interval: FiniteDuration,
                         id: String)
                        (collect: => Future[T]) = {
    import system.dispatcher
    new StatisticsCollector[T](
      scheduler = system.scheduler,
      interval = interval,
      id = id)(
      collect = collect)
  }

}