package pl.touk.nussknacker.engine.api.deployment.periodic.services

import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentDetails

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: PeriodicProcessDeploymentDetails): Map[String, String]

}
