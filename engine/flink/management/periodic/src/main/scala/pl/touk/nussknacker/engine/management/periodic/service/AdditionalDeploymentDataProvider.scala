package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.management.periodic.db.ScheduledRunDetails

import java.time.format.DateTimeFormatter

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: ScheduledRunDetails): Map[String, String]

}

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(runDetails: ScheduledRunDetails): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.processDeploymentId.value.toString,
      "runAt" -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )
  }

}