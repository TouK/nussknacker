package pl.touk.nussknacker.ui.process.displayedgraph

import pl.touk.nussknacker.engine.api.deployment.ProcessState

case class ProcessStatus(deploymentId: Option[String],
                         status: String,
                         startTime: Long,
                         isRunning: Boolean,
                         isDeployInProgress: Boolean, errorMessage: Option[String] = None)

object ProcessStatus {
  def apply(processState: ProcessState): ProcessStatus = {
    ProcessStatus(
      deploymentId = Some(processState.id.value),
      status = processState.status,
      startTime = processState.startTime,
      isRunning = processState.isOK,
      isDeployInProgress = false,
      errorMessage = processState.message
    )
  }

  def failedToGet = ProcessStatus(None, "UNKOWN", 0L, isRunning = false,
    isDeployInProgress = false, errorMessage = Some("Failed to obtain state"))
}
