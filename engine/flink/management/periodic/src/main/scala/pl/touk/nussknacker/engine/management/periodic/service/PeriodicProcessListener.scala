package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessId
import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails

import java.time.LocalDateTime

/*
  Listener is at-least-once. If there are problems e.g. with DB, invocation can be repeated for same event.
  Implementation should be aware of that. Listener is invoked during DB transaction, for that reason it's *synchronous*
 */
trait PeriodicProcessListener {

  def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit]

}

sealed trait PeriodicProcessEvent

case class DeployedEvent(runDetails: ScheduledRunDetails, externalDeploymentId: Option[ExternalDeploymentId]) extends PeriodicProcessEvent

case class FinishedEvent(runDetails: ScheduledRunDetails, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class FailedEvent(runDetails: ScheduledRunDetails, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class ScheduledEvent(id: PeriodicProcessId, runAt: LocalDateTime) extends PeriodicProcessEvent


object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = Map.empty

}