package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import cats.effect.IO
import cats.effect.kernel.Resource
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentManager,
  NoOpScenarioActivityManager,
  ProcessingTypeActionServiceStub,
  ProcessingTypeDeployedScenariosProviderStub
}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{
  ConfigWithUnresolvedVersion,
  DeploymentManagerDependencies,
  ModelData,
  ModelDependencies,
  ProcessingTypeConfig
}

object FlinkStreamingDeploymentManagerProviderHelper {

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
      deploymentManagerClassLoader: DeploymentManagersClassLoader
  ): DeploymentManager = {
    val typeConfig       = ProcessingTypeConfig.read(processingTypeConfig)
    val modelClassLoader = ModelClassLoader(typeConfig.classPath, None, deploymentManagerClassLoader)
    val modelData = ModelData(
      processingTypeConfig = typeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        _ => true,
        ComponentDefinitionExtractionMode.FinalDefinition,
      ),
      modelClassLoader
    )
    val actorSystem = ActorSystem("FlinkStreamingDeploymentManagerProviderHelper")
    val backend     = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    val deploymentManagerDependencies = DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      new ProcessingTypeActionServiceStub,
      NoOpScenarioActivityManager,
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

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
  ): Resource[IO, DeploymentManager] = {
    DeploymentManagersClassLoader
      .create(List.empty)
      .map { deploymentManagerClassLoader =>
        createDeploymentManager(processingTypeConfig, deploymentManagerClassLoader)
      }
  }

}
