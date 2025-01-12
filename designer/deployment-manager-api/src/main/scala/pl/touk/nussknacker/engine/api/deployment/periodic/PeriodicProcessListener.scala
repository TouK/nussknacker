package pl.touk.nussknacker.engine.api.deployment.periodic

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.StatusDetails
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

/*
  Listener is at-least-once. If there are problems e.g. with DB, invocation can be repeated for same event.
  Implementation should be aware of that. Listener is invoked during DB transaction, for that reason it's *synchronous*
 */
trait PeriodicProcessListener {

  def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit]
  def close(): Unit = {}
}

trait PeriodicProcessListenerFactory {
  def create(config: Config): PeriodicProcessListener
}

sealed trait PeriodicProcessEvent {
  val deployment: PeriodicProcessDeploymentDetails
}

case class DeployedEvent(
    deployment: PeriodicProcessDeploymentDetails,
    externalDeploymentId: Option[ExternalDeploymentId]
) extends PeriodicProcessEvent

case class FinishedEvent(deployment: PeriodicProcessDeploymentDetails, processState: Option[StatusDetails])
    extends PeriodicProcessEvent

case class FailedOnDeployEvent(
    deployment: PeriodicProcessDeploymentDetails,
    processState: Option[StatusDetails]
) extends PeriodicProcessEvent

case class FailedOnRunEvent(
    deployment: PeriodicProcessDeploymentDetails,
    processState: Option[StatusDetails]
) extends PeriodicProcessEvent

case class ScheduledEvent(deployment: PeriodicProcessDeploymentDetails, firstSchedule: Boolean)
    extends PeriodicProcessEvent

object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = Map.empty

}

object EmptyPeriodicProcessListenerFactory extends PeriodicProcessListenerFactory {
  override def create(config: Config): PeriodicProcessListener = EmptyListener
}
