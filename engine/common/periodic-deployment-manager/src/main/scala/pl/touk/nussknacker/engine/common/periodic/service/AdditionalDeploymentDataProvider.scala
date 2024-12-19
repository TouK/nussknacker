package pl.touk.nussknacker.engine.common.periodic.service

import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams.WithConfig
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeployment

import java.time.format.DateTimeFormatter

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: PeriodicProcessDeployment[WithConfig]): Map[String, String]

}

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(runDetails: PeriodicProcessDeployment[WithConfig]): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.id.value.toString,
      "runAt"        -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      "scheduleName" -> runDetails.scheduleName.value.getOrElse("")
    )
  }

}
