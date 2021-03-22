package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import slick.lifted.MappedTo

import java.time.LocalDateTime

case class PeriodicProcessDeployment(id: PeriodicProcessDeploymentId,
                                     periodicProcess: PeriodicProcess,
                                     runAt: LocalDateTime,
                                     state: PeriodicProcessDeploymentState)

case class PeriodicProcessDeploymentState(deployedAt: Option[LocalDateTime],
                                     completedAt: Option[LocalDateTime],
                                     status: PeriodicProcessDeploymentStatus)

object PeriodicProcessDeploymentState {
  val initial: PeriodicProcessDeploymentState = PeriodicProcessDeploymentState(deployedAt = None, completedAt = None, PeriodicProcessDeploymentStatus.Scheduled)
}

case class PeriodicProcessDeploymentId(value: Long) extends AnyVal with MappedTo[Long]

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed = Value
}
