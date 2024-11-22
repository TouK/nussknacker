package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessGen
import pl.touk.nussknacker.ui.process.periodic.model.{PeriodicProcessDeployment, PeriodicProcessDeploymentId, PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus, ScheduleName}
import pl.touk.nussknacker.ui.process.periodic.model.{PeriodicProcessDeployment, PeriodicProcessDeploymentId, PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus, ScheduleName}

import java.time.LocalDateTime

object PeriodicProcessDeploymentGen {

  val now: LocalDateTime = LocalDateTime.now()

  def apply(): PeriodicProcessDeployment[CanonicalProcess] = {
    PeriodicProcessDeployment(
      id = PeriodicProcessDeploymentId(42),
      periodicProcess = PeriodicProcessGen(),
      createdAt = now.minusMinutes(10),
      runAt = now,
      scheduleName = ScheduleName(None),
      retriesLeft = 0,
      nextRetryAt = None,
      state = PeriodicProcessDeploymentState(
        deployedAt = None,
        completedAt = None,
        status = PeriodicProcessDeploymentStatus.Scheduled,
      )
    )
  }

}
