package pl.touk.nussknacker.engine.management.jobrunner

import org.apache.flink.api.common.JobID
import pl.touk.nussknacker.engine.api.deployment.DMRunDeploymentCommand

import scala.concurrent.Future

trait FlinkScenarioJobRunner {

  def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[JobID]]

}
