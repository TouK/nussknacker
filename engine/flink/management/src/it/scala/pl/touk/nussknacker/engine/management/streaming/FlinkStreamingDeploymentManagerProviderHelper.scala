package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.{
  ConfigWithUnresolvedVersion,
  DeploymentManagerDependencies,
  ModelData,
  ProcessingTypeConfig
}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

object FlinkStreamingDeploymentManagerProviderHelper {

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
  ): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(processingTypeConfig)
    val modelData = ModelData(
      processingTypeConfig = typeConfig,
      additionalConfigsFromProvider = Map.empty,
      determineDesignerWideId = id => DesignerWideComponentId(id.toString),
      workingDirectoryOpt = None
    )
    val actorSystem       = ActorSystem("FlinkStreamingDeploymentManagerProviderHelper")
    val backend           = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    val deploymentService = new ProcessingTypeDeploymentServiceStub(List.empty)
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      deploymentService,
      actorSystem.dispatcher,
      actorSystem,
      backend
    )
    new FlinkStreamingDeploymentManagerProvider()
      .createDeploymentManager(
        modelData,
        deploymentManagerDependencies,
        typeConfig.deploymentConfig,
        None
      )
      .valueOr(err => throw new IllegalStateException(s"Invalid Deployment Manager: ${err.toList.mkString(", ")}"))
  }

}
