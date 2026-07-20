package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{Actor, Props, Timers}
import pl.touk.nussknacker.ui.process.periodic.RescheduleFinishedActor.{CheckStates, CheckStatesCompleted}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RescheduleFinishedActor {

  def props(service: PeriodicProcessService, lock: PeriodicLock, interval: FiniteDuration): Props = {
    props(service.handleFinished, lock, interval)
  }

  private[periodic] def props(handleFinished: => Future[Unit], lock: PeriodicLock, interval: FiniteDuration): Props = {
    Props(new RescheduleFinishedActor(handleFinished, lock, interval))
  }

  private case object CheckStates

  private case object CheckStatesCompleted
}

class RescheduleFinishedActor(handleFinished: => Future[Unit], lock: PeriodicLock, interval: FiniteDuration)
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
      lock.acquireOrRenew().onComplete {
        case Success(false) =>
          logger.trace("Periodic lock not acquired, skipping rescheduling")
          self ! CheckStatesCompleted
        case Success(true) =>
          logger.trace("Checking scenarios to be rescheduled or marked as failed")
          handleFinished.onComplete {
            case Success(_) =>
              self ! CheckStatesCompleted
            case Failure(exception) =>
              logger.error("Checking scenarios to be rescheduled or marked as failed finished with error", exception)
              self ! CheckStatesCompleted
          }
        case Failure(exception) =>
          logger.warn("Failed to acquire periodic lock for rescheduling", exception)
          self ! CheckStatesCompleted
      }
    case CheckStatesCompleted =>
      scheduleCheckStates()
  }

  private def scheduleCheckStates(): Unit = {
    timers.startSingleTimer(key = "checkStates", msg = CheckStates, timeout = interval)
  }

}
