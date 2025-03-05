package pl.touk.nussknacker.test.mock

import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader

trait WithTestDeploymentManagerClassLoader extends BeforeAndAfterAll {
  this: Suite =>

  private val (deploymentManagersClassLoaderInstance, releaseDeploymentManagersClassLoaderResources) =
    DeploymentManagersClassLoader
      .create(List.empty)
      .allocated
      .unsafeRunSync()

  def deploymentManagersClassLoader: DeploymentManagersClassLoader = deploymentManagersClassLoaderInstance

  override protected def afterAll(): Unit = {
    releaseDeploymentManagersClassLoaderResources.unsafeRunSync()
    super.afterAll()
  }

}
