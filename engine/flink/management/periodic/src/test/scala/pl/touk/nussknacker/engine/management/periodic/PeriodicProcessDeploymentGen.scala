package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.{
  PeriodicProcessDeployment,
  PeriodicProcessDeploymentId,
  PeriodicProcessDeploymentState,
  PeriodicProcessDeploymentStatus,
  ScheduleName
}

import java.time.LocalDateTime

object PeriodicProcessDeploymentGen {

  val now: LocalDateTime = LocalDateTime.now()

  def apply(): PeriodicProcessDeployment[WithCanonicalProcess] = {
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
