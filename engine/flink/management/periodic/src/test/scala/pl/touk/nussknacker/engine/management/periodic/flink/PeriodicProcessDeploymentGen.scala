package pl.touk.nussknacker.engine.management.periodic.flink

import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams.WithConfig
import pl.touk.nussknacker.engine.api.deployment.periodic.model._

import java.time.LocalDateTime

object PeriodicProcessDeploymentGen {

  val now: LocalDateTime = LocalDateTime.now()

  def apply(): PeriodicProcessDeployment[WithConfig] = {
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
