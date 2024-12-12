package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

sealed trait DeploymentWithJarData {
  def processVersion: ProcessVersion
  def jarFileName: String
}

object DeploymentWithJarData {

  final case class WithCanonicalProcess(
      processVersion: ProcessVersion,
      jarFileName: String,
      process: CanonicalProcess,
      inputConfigDuringExecutionJson: String,
  ) extends DeploymentWithJarData

  final case class WithoutCanonicalProcess(
      processVersion: ProcessVersion,
      jarFileName: String
  ) extends DeploymentWithJarData

}
