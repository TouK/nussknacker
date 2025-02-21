package pl.touk.nussknacker.engine.management.jobrunner

import pl.touk.nussknacker.engine.api.deployment.DMRunDeploymentCommand
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import scala.concurrent.Future

trait FlinkScenarioJobRunner {

  def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[ExternalDeploymentId]]

}
