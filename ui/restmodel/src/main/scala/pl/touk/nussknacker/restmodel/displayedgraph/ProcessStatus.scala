package pl.touk.nussknacker.restmodel.displayedgraph

import pl.touk.nussknacker.engine.api.deployment.{ProcessState, RunningState}

case class ProcessStatus(deploymentId: Option[String],
                         status: String,
                         startTime: Long,
                         isRunning: Boolean,
                         isDeployInProgress: Boolean,
                         errorMessage: Option[String] = None) {
  def isOkForDeployed: Boolean = isRunning || isDeployInProgress

}

object ProcessStatus {
  def apply(processState: ProcessState, expectedDeploymentVersion: Option[Long]): ProcessStatus = {

    val versionMatchMessage = (processState.version, expectedDeploymentVersion) match {
      //currently returning version is optional
      case (None, _) => None
      case (Some(stateVersion), Some(expectedVersion)) if stateVersion.versionId == expectedVersion => None
      case (Some(stateVersion), Some(expectedVersion)) => Some(s"Process deployed in version $stateVersion, expected version $expectedVersion")
      case (Some(stateVersion), None) => Some(s"Process deployed in version $stateVersion, should not be deployed")
    }
    val isRunning = processState.runningState == RunningState.Running && versionMatchMessage.isEmpty
    val errorMessage = List(versionMatchMessage, processState.message).flatten.reduceOption(_  + ", " + _)

    ProcessStatus(
      deploymentId = Some(processState.id.value),
      status = processState.status,
      startTime = processState.startTime,
      isRunning = isRunning,
      isDeployInProgress = processState.runningState == RunningState.Deploying,
      errorMessage = errorMessage
    )
  }

  def failedToGet = ProcessStatus(None, "UNKOWN", 0L, isRunning = false,
    isDeployInProgress = false, errorMessage = Some("Failed to obtain state"))
}
