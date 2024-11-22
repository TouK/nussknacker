package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.periodic.{DeploymentWithRuntimeParams, PeriodicDeploymentService, RuntimeParams}

import scala.concurrent.Future

class JarManagerStub extends PeriodicDeploymentService {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]]                              = Future.successful(None)
  var lastDeploymentWithRuntimeParams: Option[DeploymentWithRuntimeParams[CanonicalProcess]] = None

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess
  ): Future[DeploymentWithRuntimeParams[CanonicalProcess]] = {
    Future.successful(
      DeploymentWithRuntimeParams(
        processVersion = processVersion,
        process = canonicalProcess,
        inputConfigDuringExecutionJson = "",
        runtimeParams = RuntimeParams(Map("jarFileName" -> ""))
      )
    )
  }

  override def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams[CanonicalProcess],
      deploymentData: DeploymentData,
  ): Future[Option[ExternalDeploymentId]] = {
    lastDeploymentWithRuntimeParams = Some(deploymentWithJarData)
    deployWithJarFuture
  }

  override def cleanAfterDeployment(runtimeParams: RuntimeParams): Future[Unit] = Future.successful(())
}
