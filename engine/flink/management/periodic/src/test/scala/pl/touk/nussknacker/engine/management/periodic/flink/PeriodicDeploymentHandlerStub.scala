package pl.touk.nussknacker.engine.management.periodic.flink

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.PeriodicDeploymentHandler
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}

import scala.concurrent.Future

class PeriodicDeploymentHandlerStub extends PeriodicDeploymentHandler {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]]                       = Future.successful(None)
  var lastDeploymentWithRuntimeParams: Option[DeploymentWithRuntimeParams.WithConfig] = None

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams.WithConfig] = {
    Future.successful(
      DeploymentWithRuntimeParams.WithConfig(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = "",
        runtimeParams = RuntimeParams(Map("jarFileName" -> ""))
      )
    )
  }

  override def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams.WithConfig,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
  ): Future[Option[ExternalDeploymentId]] = {
    lastDeploymentWithRuntimeParams = Some(deploymentWithJarData)
    deployWithJarFuture
  }

  override def cleanAfterDeployment(runtimeParams: RuntimeParams): Future[Unit] = Future.successful(())
}
