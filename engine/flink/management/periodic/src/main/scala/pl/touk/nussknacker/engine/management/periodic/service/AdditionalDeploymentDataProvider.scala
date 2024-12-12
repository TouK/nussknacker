package pl.touk.nussknacker.engine.management.periodic.service

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment

import java.time.format.DateTimeFormatter

trait AdditionalDeploymentDataProvider {

  def prepareAdditionalData(runDetails: PeriodicProcessDeployment[WithCanonicalProcess]): Map[String, String]

}

object DefaultAdditionalDeploymentDataProvider extends AdditionalDeploymentDataProvider {

  override def prepareAdditionalData(
      runDetails: PeriodicProcessDeployment[WithCanonicalProcess]
  ): Map[String, String] = {
    Map(
      "deploymentId" -> runDetails.id.value.toString,
      "runAt"        -> runDetails.runAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      "scheduleName" -> runDetails.scheduleName.value.getOrElse("")
    )
  }

}
