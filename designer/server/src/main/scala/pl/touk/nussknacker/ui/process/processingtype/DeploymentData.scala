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

  def validDeploymentManagerOrStub: DeploymentManager = validDeploymentManager.valueOr(err =>
    // FIXME (next PRs): Instead of throwing this exception we should follow Null object design pattern and return some
    //                   stubbed DeploymentManager which will always return some meaningful status and not allow to run actions on scenario
    throw new IllegalStateException("Deployment Manager not available because of errors: " + err.toList.mkString(", "))
  )

  def engineSetupErrors: List[String] = validDeploymentManager.swap.map(_.toList).valueOr(_ => List.empty)

  def close(): Unit = {
    validDeploymentManager.foreach(_.close())
  }

}
