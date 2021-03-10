package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, ProcessState}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment

/*
  Listener is at-least-once. If there are problems e.g. with DB, invocation can be repeated for same event.
  Implementation should be aware of that. Listener is invoked during DB transaction, for that reason it's *synchronous*
 */
trait PeriodicProcessListener {

  def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit]

}

sealed trait PeriodicProcessEvent

case class DeployedEvent(deployment: PeriodicProcessDeployment, externalDeploymentId: Option[ExternalDeploymentId]) extends PeriodicProcessEvent

case class FinishedEvent(runDetails: PeriodicProcessDeployment, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class FailedEvent(deployment: PeriodicProcessDeployment, processState: Option[ProcessState]) extends PeriodicProcessEvent

case class ScheduledEvent(deployment: PeriodicProcessDeployment, firstSchedule: Boolean) extends PeriodicProcessEvent


object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = Map.empty

}