package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessId
import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails

import java.time.LocalDateTime
import scala.concurrent.Future

trait PeriodicProcessListener {

  def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Future[Unit]]

}

sealed trait PeriodicProcessEvent

case class DeployedEvent(runDetails: ScheduledRunDetails, externalDeploymentId: Option[ExternalDeploymentId]) extends PeriodicProcessEvent

case class FinishedEvent(runDetails: ScheduledRunDetails, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class FailedEvent(runDetails: ScheduledRunDetails, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class ScheduledEvent(id: PeriodicProcessId, runAt: LocalDateTime) extends PeriodicProcessEvent


object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Future[Unit]] = Map.empty

}