package pl.touk.nussknacker.engine.management.streaming

import _root_.sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import org.apache.pekko.actor.ActorSystem
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.classloader.{
  DeploymentManagersClassLoader,
  DeploymentManagersClassLoaderFactory,
  ModelClassLoaderFactory
}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.management.FlinkDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.savepoint.{FlinkSavepointLocator, IdentitySavepointLocator}

object FlinkDeploymentManagerProviderHelper {

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
      deploymentManagerClassLoader: DeploymentManagersClassLoader,
      savepointLocator: FlinkSavepointLocator,
  ): DeploymentManager = {
    val typeConfig       = ProcessingTypeConfig.read(processingTypeConfig)
    val modelClassLoader = ModelClassLoaderFactory.create(typeConfig.classPath, None, deploymentManagerClassLoader)
    val modelData = ModelData(
      processingTypeConfig = typeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        componentDefinitionExtractionMode = ComponentDefinitionExtractionMode.FinalDefinition,
        designerDbRef = None
      ),
      modelClassLoader
    )
    val actorSystem = ActorSystem("FlinkStreamingDeploymentManagerProviderHelper")
    val backend     = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
    val deploymentManagerDependencies = new DeploymentManagerDependencies(
      actorSystem.dispatcher,
      IORuntime.global,
      actorSystem,
      backend
    )
    new FlinkDeploymentManagerProvider()
      .createDeploymentManager(
        modelData.toModelDataProvider,
        deploymentManagerDependencies,
        typeConfig.deploymentConfig,
        None,
        savepointLocator
      )
      .valueOr(err => throw new IllegalStateException(s"Invalid Deployment Manager: ${err.toList.mkString(", ")}"))
  }

  def createDeploymentManager(
      processingTypeConfig: ConfigWithUnresolvedVersion,
  ): Resource[IO, DeploymentManager] = {
    DeploymentManagersClassLoaderFactory
      .create(List.empty)
      .map { deploymentManagerClassLoader =>
        createDeploymentManager(processingTypeConfig, deploymentManagerClassLoader, new IdentitySavepointLocator)
      }
  }

}
