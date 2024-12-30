package pl.touk.nussknacker.engine.management.streaming

import _root_.sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import akka.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment.periodic.NoOpPeriodicProcessesManagerProvider
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentManager,
  NoOpScenarioActivityManager,
  ProcessingTypeActionServiceStub,
  ProcessingTypeDeployedScenariosProviderStub
}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider

object FlinkStreamingDeploymentManagerProviderHelper {

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
  ): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(processingTypeConfig)
    val modelData = ModelData(
      processingTypeConfig = typeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        _ => true,
        ComponentDefinitionExtractionMode.FinalDefinition
      )
    )
    val actorSystem = ActorSystem("FlinkStreamingDeploymentManagerProviderHelper")
    val backend     = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      new ProcessingTypeActionServiceStub,
      NoOpScenarioActivityManager,
      NoOpPeriodicProcessesManagerProvider,
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
