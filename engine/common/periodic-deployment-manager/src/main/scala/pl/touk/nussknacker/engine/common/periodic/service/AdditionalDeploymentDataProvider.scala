package pl.touk.nussknacker.engine.common.periodic.service

import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeployment
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import java.time.format.DateTimeFormatter

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: PeriodicProcessDeployment): Map[String, String]

}

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(runDetails: PeriodicProcessDeployment): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.id.value.toString,
      "runAt"        -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      "scheduleName" -> runDetails.scheduleName.value.getOrElse("")
    )
  }

}
