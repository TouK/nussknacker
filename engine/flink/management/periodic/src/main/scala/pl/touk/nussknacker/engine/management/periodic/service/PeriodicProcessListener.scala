package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails

import scala.concurrent.Future

trait PeriodicProcessListener {

  def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Future[Unit]]

}

sealed trait PeriodicProcessEvent

case class DeployedEvent(deployedProcess: ScheduledRunDetails, externalDeploymentId: Option[ExternalDeploymentId]) extends PeriodicProcessEvent

case class FinishedEvent(deployedProcess: ScheduledRunDetails, processState: Option[ProcessState]) extends PeriodicProcessEvent


object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Future[Unit]] = Map.empty

}