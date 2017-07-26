package pl.touk.nussknacker.ui.process.displayedgraph

import pl.touk.nussknacker.engine.api.deployment.ProcessState

case class ProcessStatus(flinkJobId: Option[String], status: String, startTime: Long, isRunning: Boolean, isDeployInProgress: Boolean)

object ProcessStatus {
  def apply(processState: ProcessState): ProcessStatus = {
    ProcessStatus(
      flinkJobId = Some(processState.id),
      status = processState.status,
      startTime = processState.startTime,
      isRunning = processState.status == "RUNNING",
      isDeployInProgress = false
    )
  }
}
