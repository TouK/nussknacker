package pl.touk.nussknacker.ui.process.periodic.flink

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.PeriodicDeploymentHandler
import pl.touk.nussknacker.engine.api.deployment.PeriodicDeploymentHandler.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution

import scala.concurrent.Future

class PeriodicDeploymentHandlerStub extends PeriodicDeploymentHandler {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]]            = Future.successful(None)
  var lastDeploymentWithRuntimeParams: Option[DeploymentWithRuntimeParams] = None
  var lastInputConfigDuringExecutionJson: Option[String]                   = None

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams] = {
    Future.successful(
      DeploymentWithRuntimeParams(
        processId = Some(processVersion.processId),
        processName = processVersion.processName,
        versionId = processVersion.versionId,
        runtimeParams = RuntimeParams(Map("jarFileName" -> ""))
      )
    )
  }

  override def provideInputConfigDuringExecutionJson(): Future[InputConfigDuringExecution] =
    Future.successful(InputConfigDuringExecution(ConfigFactory.parseString("")))

  override def deployWithRuntimeParams(
      deploymentWithJarData: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      processVersion: ProcessVersion,
  ): Future[Option[ExternalDeploymentId]] = {
    lastDeploymentWithRuntimeParams = Some(deploymentWithJarData)
    lastInputConfigDuringExecutionJson = Some(inputConfigDuringExecutionJson)
    deployWithJarFuture
  }

  override def cleanAfterDeployment(runtimeParams: RuntimeParams): Future[Unit] = Future.successful(())

}
