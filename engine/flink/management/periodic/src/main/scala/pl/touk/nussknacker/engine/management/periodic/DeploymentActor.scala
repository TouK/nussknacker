package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props, Timers}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.DeploymentActor.{
  CheckToBeDeployed,
  DeploymentCompleted,
  WaitingForDeployment
}
import pl.touk.nussknacker.engine.management.periodic.model.{
  PeriodicProcessDeployment,
  PeriodicProcessDeploymentWithFullProcess
}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object DeploymentActor {

  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    props(service.findToBeDeployed, service.deploy, interval)
  }

  private[periodic] def props(
      findToBeDeployed: => Future[Seq[PeriodicProcessDeploymentWithFullProcess]],
      deploy: PeriodicProcessDeploymentWithFullProcess => Future[Unit],
      interval: FiniteDuration
  ) = {
    Props(new DeploymentActor(findToBeDeployed, deploy, interval))
  }

  private[periodic] case object CheckToBeDeployed

  private case class WaitingForDeployment(ids: List[PeriodicProcessDeploymentWithFullProcess])

  private case object DeploymentCompleted
}

class DeploymentActor(
    findToBeDeployed: => Future[Seq[PeriodicProcessDeploymentWithFullProcess]],
    deploy: PeriodicProcessDeploymentWithFullProcess => Future[Unit],
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
          logger.debug(s"Found ${runDetailsSeq.size} to be deployed: ${runDetailsSeq.map(_.deployment.display)}")
          self ! WaitingForDeployment(runDetailsSeq.toList)
        case Failure(exception) =>
          logger.error("Finding scenarios to be deployed failed unexpectedly", exception)
      }
    case WaitingForDeployment(Nil) =>
    case WaitingForDeployment(runDetails :: _) =>
      logger.info(s"Found a scenario to be deployed: ${runDetails.deployment.display}")
      context.become(receiveOngoingDeployment(runDetails.deployment))
      deploy(runDetails) onComplete {
        case Success(_) =>
          self ! DeploymentCompleted
        case Failure(exception) =>
          logger.error(s"Deployment of ${runDetails.deployment.display} failed unexpectedly", exception)
          self ! DeploymentCompleted
      }
  }

  private def receiveOngoingDeployment(runDetails: PeriodicProcessDeployment): Receive = {
    case CheckToBeDeployed =>
      logger.debug(s"Still waiting for ${runDetails.display} to be deployed")
    case DeploymentCompleted =>
      context.unbecome()
  }

}
