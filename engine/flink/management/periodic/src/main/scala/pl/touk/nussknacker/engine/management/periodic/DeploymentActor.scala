package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails
import pl.touk.nussknacker.engine.management.periodic.DeploymentActor.{CheckToBeDeployed, DeploymentCompleted, WaitingForDeployment}

import scala.concurrent.duration._

object DeploymentActor {

  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    Props(new DeploymentActor(service, interval))
  }

  case object CheckToBeDeployed

  case class WaitingForDeployment(ids: List[ScheduledRunDetails])

  case class DeploymentCompleted(success: Boolean)
}

class DeploymentActor(service: PeriodicProcessService, interval: FiniteDuration) extends Actor with LazyLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    context.system.scheduler.schedule(initialDelay = Duration.Zero, interval = interval, receiver = self, message = CheckToBeDeployed)
  }

  override def receive: Receive = {
    case CheckToBeDeployed =>
      logger.debug("Checking processes to be deployed")
      service.findToBeDeployed.foreach { runDetails => self ! WaitingForDeployment(runDetails.toList) }
    case WaitingForDeployment(Nil) =>
    case WaitingForDeployment(runDetails :: _) =>
      logger.info("Found a process to be deployed: {}", runDetails.processDeploymentId)
      service.deploy(runDetails) onComplete { result => self ! DeploymentCompleted(result.isSuccess) }
      context.become(ongoingDeployment(runDetails))
  }

  private def ongoingDeployment(runDetails: ScheduledRunDetails): Receive = {
    case CheckToBeDeployed =>
    case DeploymentCompleted(success) =>
      logger.info("Deployment {} completed, success: {}", runDetails.processDeploymentId, success)
      context.unbecome()
  }
}
