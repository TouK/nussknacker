package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.deployment.ProcessState

case class ProcessStatus(id: String, status: String, startTime: Long, isRunning: Boolean)

object ProcessStatus {
  def apply(processState: ProcessState): ProcessStatus = {
    ProcessStatus(
      id = processState.id,
      status = processState.status,
      startTime = processState.startTime,
      isRunning = processState.status == "RUNNING"
    )
  }
}
