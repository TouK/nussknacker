package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.PeriodicDeploymentHandler.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import scala.concurrent.Future

trait PeriodicDeploymentHandler {

  def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams]

  def provideInputConfigDuringExecutionJson(): Future[InputConfigDuringExecution]

  def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      processVersion: ProcessVersion,
  ): Future[Option[ExternalDeploymentId]]

  def cleanAfterDeployment(
      runtimeParams: RuntimeParams
  ): Future[Unit]

}

object PeriodicDeploymentHandler {

  final case class DeploymentWithRuntimeParams(
      processId: Option[ProcessId],
      processName: ProcessName,
      versionId: VersionId,
      runtimeParams: RuntimeParams,
  )

  final case class RuntimeParams(params: Map[String, String])

}
