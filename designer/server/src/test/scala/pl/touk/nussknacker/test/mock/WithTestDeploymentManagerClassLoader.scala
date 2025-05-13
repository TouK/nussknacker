package pl.touk.nussknacker.test.mock

import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.classloader.{DeploymentManagersClassLoader, DeploymentManagersClassLoaderFactory}

trait WithTestDeploymentManagerClassLoader extends BeforeAndAfterAll {
  this: Suite =>

  private val (deploymentManagersClassLoaderInstance, releaseDeploymentManagersClassLoaderResources) =
    DeploymentManagersClassLoaderFactory
      .create(List.empty)
      .allocated
      .unsafeRunSync()

  def deploymentManagersClassLoader: DeploymentManagersClassLoader = deploymentManagersClassLoaderInstance

  override protected def afterAll(): Unit = {
    releaseDeploymentManagersClassLoaderResources.unsafeRunSync()
    super.afterAll()
  }

}
