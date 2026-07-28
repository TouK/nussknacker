package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{Actor, Props, Timers}
import org.apache.pekko.pattern.pipe
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeployment

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object DeploymentActor {

  def props(
      service: PeriodicProcessService,
      lock: PeriodicLock,
      interval: FiniteDuration
  ): Props = {
    props(service.findToBeDeployed, service.deploy, lock, interval)
  }

  private[periodic] def props(
      findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
      deploy: PeriodicProcessDeployment => Future[Unit],
      lock: PeriodicLock,
      interval: FiniteDuration
  ): Props = {
    Props(new DeploymentActor(findToBeDeployed, deploy, lock, interval))
  }

  private sealed trait Msg

  private object Msg {
    case object CheckToBeDeployed                                         extends Msg
    case class WaitingForDeployment(ids: List[PeriodicProcessDeployment]) extends Msg
    case class LockAcquisitionResult(result: Try[Boolean])                extends Msg
    case object DeploymentCompleted                                       extends Msg
  }

}

class DeploymentActor(
    findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
    deploy: PeriodicProcessDeployment => Future[Unit],
    lock: PeriodicLock,
    interval: FiniteDuration
) extends Actor
    with Timers
    with LazyLogging {

  import context.dispatcher

  import DeploymentActor._
  import DeploymentActor.Msg._

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startTimerAtFixedRate(key = "checkToBeDeployed", msg = CheckToBeDeployed, interval = interval)
  }

  override def receive: Receive = inState(handleIdle)

  private def inState(f: Msg => Unit): Receive = { case msg: Msg => f(msg) }

  private def handleIdle(msg: Msg): Unit = msg match {
    case CheckToBeDeployed =>
      logger.trace("Checking scenarios to be deployed")
      findToBeDeployed
        .map { runDetailsSeq =>
          logger.debug(s"Found ${runDetailsSeq.size} to be deployed: ${runDetailsSeq.map(_.display)}")
          WaitingForDeployment(runDetailsSeq.toList)
        }
        .recover { case ex =>
          logger.error("Finding scenarios to be deployed failed unexpectedly", ex)
          WaitingForDeployment(Nil)
        }
        .pipeTo(self)
    case WaitingForDeployment(Nil) => ()
    case WaitingForDeployment(runDetails :: _) =>
      context.become(inState(handleAcquiringLock(runDetails)))
      lock
        .acquireOrRenew()
        .transform(r => Success(LockAcquisitionResult(r))) pipeTo self
    case LockAcquisitionResult(_) => ()
    case DeploymentCompleted      => ()
  }

  private def handleAcquiringLock(runDetails: PeriodicProcessDeployment)(msg: Msg): Unit = msg match {
    case CheckToBeDeployed       => ()
    case WaitingForDeployment(_) => ()
    case LockAcquisitionResult(Success(true)) =>
      logger.info(s"Found a scenario to be deployed: ${runDetails.display}")
      context.become(inState(handleOngoingDeployment(runDetails)))
      deploy(runDetails)
        .map(_ => DeploymentCompleted)
        .recover { case ex =>
          logger.error(s"Deployment of ${runDetails.display} failed unexpectedly", ex)
          DeploymentCompleted
        }
        .pipeTo(self)
    case LockAcquisitionResult(Success(false)) =>
      logger.trace("Deployment lock not acquired, skipping")
      context.become(inState(handleIdle))
    case LockAcquisitionResult(Failure(exception)) =>
      logger.warn("Failed to acquire deployment lock", exception)
      context.become(inState(handleIdle))
    case DeploymentCompleted => ()
  }

  private def handleOngoingDeployment(runDetails: PeriodicProcessDeployment)(msg: Msg): Unit = msg match {
    case CheckToBeDeployed =>
      // TODO: on lock loss, update deployment status in DB so other nodes don't re-deploy the same scenario
      lock.acquireOrRenew().onComplete {
        case Success(false)     => logger.warn(s"Lost deployment lock while deploying ${runDetails.display}")
        case Failure(exception) => logger.warn(s"Failed to renew deployment lock for ${runDetails.display}", exception)
        case _                  => ()
      }
    case WaitingForDeployment(_)  => ()
    case LockAcquisitionResult(_) => ()
    case DeploymentCompleted      => context.become(inState(handleIdle))
  }

}
