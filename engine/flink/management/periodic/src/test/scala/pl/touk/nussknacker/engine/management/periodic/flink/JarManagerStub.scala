package pl.touk.nussknacker.engine.management.periodic.flink

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.PeriodicDeploymentService
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}

import scala.concurrent.Future

class JarManagerStub extends PeriodicDeploymentService {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]]            = Future.successful(None)
  var lastDeploymentWithRuntimeParams: Option[DeploymentWithRuntimeParams] = None

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams] = {
    Future.successful(
      DeploymentWithRuntimeParams(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = "",
        runtimeParams = RuntimeParams(Map("jarFileName" -> ""))
      )
    )
  }

  override def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
  ): Future[Option[ExternalDeploymentId]] = {
    lastDeploymentWithRuntimeParams = Some(deploymentWithJarData)
    deployWithJarFuture
  }

  override def cleanAfterDeployment(runtimeParams: RuntimeParams): Future[Unit] = Future.successful(())
}
