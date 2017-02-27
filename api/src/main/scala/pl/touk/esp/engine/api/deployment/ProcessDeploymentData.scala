package pl.touk.esp.engine.api.deployment

sealed trait ProcessDeploymentData

case class GraphProcess(processAsJson: String) extends ProcessDeploymentData

case class CustomProcess(mainClass: String) extends ProcessDeploymentData

case class DeploymentData(processId: String, processJson: String)