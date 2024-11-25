package pl.touk.nussknacker.engine.common.periodic

import akka.actor.{Actor, Props, Timers}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.common.periodic.RescheduleFinishedActor.{CheckStates, CheckStatesCompleted}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RescheduleFinishedActor {

  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    props(service.handleFinished, interval)
  }

  private[periodic] def props(handleFinished: => Future[Unit], interval: FiniteDuration): Props = {
    Props(new RescheduleFinishedActor(handleFinished, interval))
  }

  private case object CheckStates

  private case object CheckStatesCompleted
}

class RescheduleFinishedActor(handleFinished: => Future[Unit], interval: FiniteDuration)
    extends Actor
    with Timers
    with LazyLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    scheduleCheckStates()
  }

  override def receive: Receive = {
    case CheckStates =>
      logger.trace("Checking scenarios to be rescheduled or marked as failed")
      handleFinished onComplete {
        case Success(_) =>
          self ! CheckStatesCompleted
        case Failure(exception) =>
          logger.error("Checking scenarios to be rescheduled or marked as failed finished with error", exception)
          self ! CheckStatesCompleted
      }
    case CheckStatesCompleted =>
      scheduleCheckStates()
  }

  private def scheduleCheckStates(): Unit = {
    timers.startSingleTimer(key = "checkStates", msg = CheckStates, timeout = interval)
  }

}
