package pl.touk.nussknacker.ui.process.deployment.deploymentstatus

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  DeploymentManager,
  StatusDetails,
  WithDataFreshnessStatus
}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.util.FutureUtils.FutureOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object DeploymentManagerReliableStatusesWrapper {

  implicit class Ops(deploymentManager: DeploymentManager) {

    def getScenarioDeploymentsStatusesWithTimeoutOpt(scenarioName: ProcessName, timeoutOpt: Option[FiniteDuration])(
        implicit freshnessPolicy: DataFreshnessPolicy,
        actorSystem: ActorSystem
    ): Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[StatusDetails]]]] = {
      import actorSystem._
      val deploymentStatusesOptFuture
          : Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[StatusDetails]]]] =
        deploymentManager
          .getScenarioDeploymentsStatuses(scenarioName)
          .map(Right(_))
          .recover { case NonFatal(e) => Left(GetDeploymentsStatusesFailure(scenarioName, e)) }

      timeoutOpt
        .map { timeout =>
          deploymentStatusesOptFuture
            .withTimeout(timeout, timeoutResult = Left(GetDeploymentsStatusTimeout(scenarioName)))
        }
        .getOrElse(deploymentStatusesOptFuture)
    }

  }

}

sealed abstract class GetDeploymentsStatusesError(message: String, cause: Throwable) extends Exception(message, cause)

case class GetDeploymentsStatusesFailure(scenarioName: ProcessName, cause: Throwable)
    extends GetDeploymentsStatusesError(s"Failure during getting deployment statuses for scenario $scenarioName", cause)

case class GetDeploymentsStatusTimeout(scenarioName: ProcessName)
    extends GetDeploymentsStatusesError(s"Timeout during getting deployment statuses for scenario $scenarioName", null)
