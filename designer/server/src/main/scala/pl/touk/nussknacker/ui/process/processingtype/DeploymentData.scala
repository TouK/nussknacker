package pl.touk.nussknacker.ui.process.processingtype

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{CustomProcessValidator, MetaDataInitializer}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.DeploymentManagerType
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName

final class DeploymentData(
    val deploymentManagerType: DeploymentManagerType,
    validDeploymentManager: ValidatedNel[String, DeploymentManager],
    val metaDataInitializer: MetaDataInitializer,
    val deploymentScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    val additionalValidators: List[CustomProcessValidator],
    val engineSetupName: EngineSetupName
) {

  def validDeploymentManagerOrStub: DeploymentManager =
    validDeploymentManager.valueOr(_ => InvalidDeploymentManagerStub)

  def engineSetupErrors: List[String] = validDeploymentManager.swap.map(_.toList).valueOr(_ => List.empty)

}
