package pl.touk.nussknacker.engine.api.deployment.scheduler.services

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import scala.concurrent.Future

trait ScheduledExecutionPerformer {

  def provideInputConfigDuringExecutionJson(): Future[InputConfigDuringExecution]

  def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams]

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
