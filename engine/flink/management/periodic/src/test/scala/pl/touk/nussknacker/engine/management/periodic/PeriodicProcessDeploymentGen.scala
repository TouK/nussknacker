package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository.createPeriodicProcessMetadata
import pl.touk.nussknacker.engine.management.periodic.model._

import java.time.LocalDateTime

object PeriodicProcessDeploymentGen {

  val now: LocalDateTime = LocalDateTime.now()

  def apply(): PeriodicProcessDeploymentWithFullProcess = {
    val periodicProcess = PeriodicProcessGen()
    PeriodicProcessDeploymentWithFullProcess(
      PeriodicProcessDeployment(
        id = PeriodicProcessDeploymentId(42),
        periodicProcessMetadata = createPeriodicProcessMetadata(periodicProcess),
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
      ),
      periodicProcess.deploymentData.process,
      periodicProcess.deploymentData.inputConfigDuringExecutionJson,
    )

  }

}
