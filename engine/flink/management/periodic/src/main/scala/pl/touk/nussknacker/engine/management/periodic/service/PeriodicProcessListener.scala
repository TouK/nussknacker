package pl.touk.nussknacker.engine.management.periodic.service

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.StatusDetails
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment

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
  val deployment: PeriodicProcessDeployment[WithCanonicalProcess]
}

case class DeployedEvent(
    deployment: PeriodicProcessDeployment[WithCanonicalProcess],
    externalDeploymentId: Option[ExternalDeploymentId]
) extends PeriodicProcessEvent

case class FinishedEvent(
    deployment: PeriodicProcessDeployment[WithCanonicalProcess],
    processState: Option[StatusDetails]
) extends PeriodicProcessEvent

case class FailedOnDeployEvent(
    deployment: PeriodicProcessDeployment[WithCanonicalProcess],
    processState: Option[StatusDetails]
) extends PeriodicProcessEvent

case class FailedOnRunEvent(
    deployment: PeriodicProcessDeployment[WithCanonicalProcess],
    processState: Option[StatusDetails]
) extends PeriodicProcessEvent

case class ScheduledEvent(deployment: PeriodicProcessDeployment[WithCanonicalProcess], firstSchedule: Boolean)
    extends PeriodicProcessEvent

object EmptyListener extends EmptyListener

trait EmptyListener extends PeriodicProcessListener {

  override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = Map.empty

}

object EmptyPeriodicProcessListenerFactory extends PeriodicProcessListenerFactory {
  override def create(config: Config): PeriodicProcessListener = EmptyListener
}
