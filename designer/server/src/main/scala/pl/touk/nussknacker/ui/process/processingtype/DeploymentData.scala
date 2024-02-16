package pl.touk.nussknacker.ui.process.processingtype

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{CustomProcessValidator, MetaDataInitializer}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName

final case class DeploymentData(
    validDeploymentManager: ValidatedNel[String, DeploymentManager],
    metaDataInitializer: MetaDataInitializer,
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    engineSetupName: EngineSetupName
) {

  def validDeploymentManagerOrStub: DeploymentManager =
    validDeploymentManager.valueOr(_ => InvalidDeploymentManagerStub)

  def engineSetupErrors: List[String] = validDeploymentManager.swap.map(_.toList).valueOr(_ => List.empty)

  def close(): Unit = {
    validDeploymentManager.foreach(_.close())
  }

}
