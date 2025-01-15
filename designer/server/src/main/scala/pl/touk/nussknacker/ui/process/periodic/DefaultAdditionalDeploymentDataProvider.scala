package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.scheduler.model.ScheduledDeploymentDetails
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.AdditionalDeploymentDataProvider

import java.time.format.DateTimeFormatter

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(runDetails: ScheduledDeploymentDetails): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.id.toString,
      "runAt"        -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      "scheduleName" -> runDetails.scheduleName.getOrElse("")
    )
  }

}
