package pl.touk.nussknacker.engine.api.deployment.periodic

import java.time.format.DateTimeFormatter

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: PeriodicProcessDeploymentDetails): Map[String, String]

}

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(runDetails: PeriodicProcessDeploymentDetails): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.id.toString,
      "runAt"        -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      "scheduleName" -> runDetails.scheduleName.getOrElse("")
    )
  }

}
