package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{Actor, Props, Timers}
import org.apache.pekko.pattern.pipe
import pl.touk.nussknacker.ui.process.periodic.RescheduleFinishedActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object RescheduleFinishedActor {

  def props(service: PeriodicProcessService, lock: PeriodicLock, interval: FiniteDuration): Props = {
    props(service.handleFinished, lock, interval)
  }

  private[periodic] def props(handleFinished: => Future[Unit], lock: PeriodicLock, interval: FiniteDuration): Props = {
    Props(new RescheduleFinishedActor(handleFinished, lock, interval))
  }

  private sealed trait Msg

  private object Msg {
    case object CheckStates                     extends Msg
    case class LockResult(result: Try[Boolean]) extends Msg
    case object CheckStatesCompleted            extends Msg
  }

}

class RescheduleFinishedActor(handleFinished: => Future[Unit], lock: PeriodicLock, interval: FiniteDuration)
    extends Actor
    with Timers
    with LazyLogging {

  import context.dispatcher

  import RescheduleFinishedActor.Msg._

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startTimerAtFixedRate(key = "checkStates", msg = CheckStates, interval = interval)
  }

  override def receive: Receive = inState(handleIdle)

  private def inState(f: Msg => Unit): Receive = { case msg: Msg => f(msg) }

  private def handleIdle(msg: Msg): Unit = msg match {
    case CheckStates =>
      context.become(inState(handleAcquiringLock))
      lock.acquireOrRenew().transform(r => Success(LockResult(r))).pipeTo(self)
    case LockResult(_)        => ()
    case CheckStatesCompleted => ()
  }

  private def handleAcquiringLock(msg: Msg): Unit = msg match {
    case CheckStates => ()
    case LockResult(Success(true)) =>
      logger.trace("Checking scenarios to be rescheduled or marked as failed")
      context.become(inState(handleRunning))
      handleFinished
        .map(_ => CheckStatesCompleted)
        .recover { case ex =>
          logger.error("Checking scenarios to be rescheduled or marked as failed finished with error", ex)
          CheckStatesCompleted
        }
        .pipeTo(self)
    case LockResult(Success(false)) =>
      logger.trace("Periodic lock not acquired, skipping rescheduling")
      context.become(inState(handleIdle))
    case LockResult(Failure(ex)) =>
      logger.warn("Failed to acquire periodic lock for rescheduling", ex)
      context.become(inState(handleIdle))
    case CheckStatesCompleted => ()
  }

  private def handleRunning(msg: Msg): Unit = msg match {
    case CheckStates =>
      lock.acquireOrRenew().onComplete {
        case Success(false) => logger.warn("Periodic lock not renewed — another instance may have taken over")
        case Failure(ex)    => logger.warn("Failed to renew periodic lock", ex)
        case _              => ()
      }
    case LockResult(_)        => ()
    case CheckStatesCompleted => context.become(inState(handleIdle))
  }

}
