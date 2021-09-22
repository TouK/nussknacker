package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props, Timers}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.DeploymentActor.{CheckToBeDeployed, DeploymentCompleted, WaitingForDeployment}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment

import scala.concurrent.duration._

object DeploymentActor {

  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    Props(new DeploymentActor(service, interval))
  }

  private case object CheckToBeDeployed

  private case class WaitingForDeployment(ids: List[PeriodicProcessDeployment])

  private case class DeploymentCompleted(success: Boolean)
}

class DeploymentActor(service: PeriodicProcessService, interval: FiniteDuration) extends Actor
  with Timers
  with LazyLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startPeriodicTimer(key = "checkToBeDeployed", msg = CheckToBeDeployed, interval = interval)
  }

  override def receive: Receive = {
    case CheckToBeDeployed =>
      logger.debug("Checking scenarios to be deployed")
      service.findToBeDeployed.foreach { runDetails => self ! WaitingForDeployment(runDetails.toList) }
    case WaitingForDeployment(Nil) =>
    case WaitingForDeployment(runDetails :: _) =>
      logger.info("Found a scenario to be deployed: {}", runDetails.id)
      context.become(receiveOngoingDeployment(runDetails))
      service.deploy(runDetails) onComplete { result => self ! DeploymentCompleted(result.isSuccess) }
  }

  private def receiveOngoingDeployment(runDetails: PeriodicProcessDeployment): Receive = {
    case CheckToBeDeployed =>
    case DeploymentCompleted(success) =>
      logger.info("Deployment {} completed, success: {}", runDetails.id, success)
      context.unbecome()
  }
}
