package pl.touk.nussknacker.engine.api.deployment.scheduler.services

import pl.touk.nussknacker.engine.api.deployment.scheduler.model.ScheduledDeploymentDetails

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: ScheduledDeploymentDetails): Map[String, String]

}
