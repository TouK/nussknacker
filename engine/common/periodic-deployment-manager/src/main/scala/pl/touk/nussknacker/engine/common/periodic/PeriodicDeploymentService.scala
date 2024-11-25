package pl.touk.nussknacker.engine.common.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}

import scala.concurrent.Future

trait PeriodicDeploymentService {

  def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess
  ): Future[DeploymentWithRuntimeParams[CanonicalProcess]]

  def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams[CanonicalProcess],
      deploymentData: DeploymentData,
  ): Future[Option[ExternalDeploymentId]]

  def cleanAfterDeployment(
      runtimeParams: RuntimeParams
  ): Future[Unit]

}
