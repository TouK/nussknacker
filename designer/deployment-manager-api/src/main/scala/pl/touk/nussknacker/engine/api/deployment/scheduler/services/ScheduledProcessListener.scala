package pl.touk.nussknacker.engine.api.deployment.scheduler.services

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatusDetails
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.ScheduledDeploymentDetails
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

/*
  Listener is at-least-once. If there are problems e.g. with DB, invocation can be repeated for same event.
  Implementation should be aware of that. Listener is invoked during DB transaction, for that reason it's *synchronous*
 */
trait ScheduledProcessListener {

  def onScheduledProcessEvent: PartialFunction[ScheduledProcessEvent, Unit]
  def close(): Unit = {}
}

trait ScheduledProcessListenerFactory {
  def create(config: Config): ScheduledProcessListener
}

sealed trait ScheduledProcessEvent {
  val deployment: ScheduledDeploymentDetails
}

case class DeployedEvent(
    deployment: ScheduledDeploymentDetails,
    externalDeploymentId: Option[ExternalDeploymentId]
) extends ScheduledProcessEvent

case class FinishedEvent(
    deployment: ScheduledDeploymentDetails,
    canonicalProcess: CanonicalProcess,
    processState: Option[DeploymentStatusDetails]
) extends ScheduledProcessEvent

case class FailedOnDeployEvent(
    deployment: ScheduledDeploymentDetails,
    processState: Option[DeploymentStatusDetails]
) extends ScheduledProcessEvent

case class FailedOnRunEvent(
    deployment: ScheduledDeploymentDetails,
    processState: Option[DeploymentStatusDetails]
) extends ScheduledProcessEvent

case class ScheduledEvent(deployment: ScheduledDeploymentDetails, firstSchedule: Boolean) extends ScheduledProcessEvent

object EmptyListener extends EmptyListener

trait EmptyListener extends ScheduledProcessListener {

  override def onScheduledProcessEvent: PartialFunction[ScheduledProcessEvent, Unit] = Map.empty

}

object EmptyScheduledProcessListenerFactory extends ScheduledProcessListenerFactory {
  override def create(config: Config): ScheduledProcessListener = EmptyListener
}
