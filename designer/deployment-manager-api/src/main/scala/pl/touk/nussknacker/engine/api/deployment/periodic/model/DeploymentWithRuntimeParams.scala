package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion

sealed trait DeploymentWithRuntimeParams {
  def processVersion: ProcessVersion
  def runtimeParams: RuntimeParams
}

object DeploymentWithRuntimeParams {

  final case class WithConfig(
      processVersion: ProcessVersion,
      runtimeParams: RuntimeParams,
      inputConfigDuringExecutionJson: String,
  ) extends DeploymentWithRuntimeParams

  final case class WithoutConfig(
      processVersion: ProcessVersion,
      runtimeParams: RuntimeParams,
  ) extends DeploymentWithRuntimeParams

}

final case class RuntimeParams(params: Map[String, String])
