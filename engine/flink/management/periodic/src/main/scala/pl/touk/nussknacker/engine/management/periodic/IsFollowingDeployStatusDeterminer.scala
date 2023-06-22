package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.management.periodic.PeriodicStateStatus.ScheduledStatus

object IsFollowingDeployStatusDeterminer {

  def isFollowingDeployStatus(status: StateStatus): Boolean = {
    SimpleStateStatus.DefaultFollowingDeployStatuses.contains(status) ||
      ScheduledStatus.isScheduledStatus(status)
  }

}
