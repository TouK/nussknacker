package pl.touk.nussknacker.engine.management.periodic

import akka.actor.{Actor, Props, Timers}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.management.periodic.DeploymentActor.{CheckToBeDeployed, DeploymentCompleted, WaitingForDeployment}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment

import scala.concurrent.Future
import scala.concurrent.duration._

object DeploymentActor {

  def props(service: PeriodicProcessService, interval: FiniteDuration): Props = {
    props(service.findToBeDeployed, service.deploy, interval)
  }

  private[periodic] def props(findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
                              deploy: PeriodicProcessDeployment => Future[Unit],
                              interval: FiniteDuration) = {
    Props(new DeploymentActor(findToBeDeployed, deploy, interval))
  }

  private[periodic] case object CheckToBeDeployed

  private case class WaitingForDeployment(ids: List[PeriodicProcessDeployment])

  private case class DeploymentCompleted(success: Boolean)
}

class DeploymentActor(findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
                      deploy: PeriodicProcessDeployment => Future[Unit],
                      interval: FiniteDuration) extends Actor
  with Timers
  with LazyLogging {

  import context.dispatcher

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startTimerAtFixedRate(key = "checkToBeDeployed", msg = CheckToBeDeployed, interval = interval)
  }

  override def receive: Receive = {
    case CheckToBeDeployed =>
      logger.debug("Checking scenarios to be deployed")
      findToBeDeployed.foreach { runDetails => self ! WaitingForDeployment(runDetails.toList) }
    case WaitingForDeployment(Nil) =>
    case WaitingForDeployment(runDetails :: _) =>
      logger.info("Found a scenario to be deployed: {}", runDetails.id)
      context.become(receiveOngoingDeployment(runDetails))
      deploy(runDetails) onComplete { result => self ! DeploymentCompleted(result.isSuccess) }
  }

  private def receiveOngoingDeployment(runDetails: PeriodicProcessDeployment): Receive = {
    case CheckToBeDeployed =>
    case DeploymentCompleted(success) =>
      logger.info("Deployment {} completed, success: {}", runDetails.id, success)
      context.unbecome()
  }
}
