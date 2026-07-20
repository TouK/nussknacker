package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{Actor, Props, Timers}
import org.apache.pekko.pattern.pipe
import pl.touk.nussknacker.ui.process.periodic.DeploymentActor._
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

  private[periodic] case object CheckToBeDeployed

  private case class WaitingForDeployment(ids: List[PeriodicProcessDeployment])

  private case class LockAcquisitionResult(result: Try[Boolean])

  private case object DeploymentCompleted
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

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startTimerAtFixedRate(key = "checkToBeDeployed", msg = CheckToBeDeployed, interval = interval)
  }

  override def receive: Receive = {
    case CheckToBeDeployed =>
      logger.trace("Checking scenarios to be deployed")
      findToBeDeployed.onComplete {
        case Success(runDetailsSeq) =>
          logger.debug(s"Found ${runDetailsSeq.size} to be deployed: ${runDetailsSeq.map(_.display)}")
          self ! WaitingForDeployment(runDetailsSeq.toList)
        case Failure(exception) =>
          logger.error("Finding scenarios to be deployed failed unexpectedly", exception)
      }
    case WaitingForDeployment(Nil) =>
    case WaitingForDeployment(runDetails :: _) =>
      context.become(acquiringLock(runDetails))
      lock
        .acquireOrRenew()
        .transform(r => Success(LockAcquisitionResult(r))) pipeTo self
  }

  private def acquiringLock(runDetails: PeriodicProcessDeployment): Receive = {
    case CheckToBeDeployed => ()
    case LockAcquisitionResult(Success(true)) =>
      logger.info(s"Found a scenario to be deployed: ${runDetails.display}")
      context.become(receiveOngoingDeployment(runDetails))
      deploy(runDetails).onComplete {
        case Success(_) =>
          self ! DeploymentCompleted
        case Failure(exception) =>
          logger.error(s"Deployment of ${runDetails.display} failed unexpectedly", exception)
          self ! DeploymentCompleted
      }
    case LockAcquisitionResult(Success(false)) =>
      logger.trace("Deployment lock not acquired, skipping")
      context.become(receive)
    case LockAcquisitionResult(Failure(exception)) =>
      logger.warn("Failed to acquire deployment lock", exception)
      context.become(receive)
  }

  private def receiveOngoingDeployment(
      runDetails: PeriodicProcessDeployment
  ): Receive = {
    case CheckToBeDeployed =>
      lock.acquireOrRenew().onComplete {
        case Success(false)     => logger.warn(s"Lost deployment lock while deploying ${runDetails.display}")
        case Failure(exception) => logger.warn(s"Failed to renew deployment lock for ${runDetails.display}", exception)
        case _                  => ()
      }
    case DeploymentCompleted =>
      context.become(receive)
  }

}
