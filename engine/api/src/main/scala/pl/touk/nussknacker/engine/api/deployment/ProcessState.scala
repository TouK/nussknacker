package pl.touk.nussknacker.engine.api.deployment

//TODO: better information
case class ProcessState(id: DeploymentId, status: String, startTime: Long) {
  def isRunning: Boolean = status == "RUNNING"
}
