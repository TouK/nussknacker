package pl.touk.nussknacker.ui.process.processingtype

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{CustomProcessValidator, MetaDataInitializer}
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.properties.ScenarioProperties
import pl.touk.nussknacker.engine.deployment.EngineSetupName

final case class DeploymentData(
    validDeploymentManager: ValidatedNel[String, DeploymentManager],
    metaDataInitializer: MetaDataInitializer,
    scenarioPropertiesConfig: ScenarioProperties,
    fragmentPropertiesConfig: Map[String, SingleScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    deploymentManagerType: DeploymentManagerType,
    engineSetupName: EngineSetupName
) {

  def validDeploymentManagerOrStub: DeploymentManager =
    validDeploymentManager.valueOr(_ => InvalidDeploymentManagerStub)

  def engineSetupErrors: List[String] = validDeploymentManager.swap.map(_.toList).valueOr(_ => List.empty)

  def close(): Unit = {
    validDeploymentManager.foreach(_.close())
  }

}

case class DeploymentManagerType(value: String)
