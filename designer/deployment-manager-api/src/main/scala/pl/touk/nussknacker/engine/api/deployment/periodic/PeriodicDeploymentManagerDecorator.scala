package pl.touk.nussknacker.engine.api.deployment.periodic

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager

trait PeriodicDeploymentManagerDecorator {

  def decorate(
      underlying: DeploymentManager,
      engineHandler: PeriodicDeploymentEngineHandler,
      deploymentConfig: Config,
      dependencies: DeploymentManagerDependencies,
  ): DeploymentManager

}
