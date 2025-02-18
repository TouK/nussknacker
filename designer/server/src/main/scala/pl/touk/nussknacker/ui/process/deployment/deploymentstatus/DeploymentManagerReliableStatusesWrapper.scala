package pl.touk.nussknacker.ui.process.deployment.deploymentstatus

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, StatusDetails, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FutureUtils.FutureOps

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object DeploymentManagerReliableStatusesWrapper {

  implicit class Ops(dmDispatcher: DeploymentManagerDispatcher) {

    def getScenarioDeploymentsStatusesWithErrorWrappingAndTimeoutOpt(
        processingType: ProcessingType,
        scenarioName: ProcessName,
        timeoutOpt: Option[FiniteDuration]
    )(
        implicit user: LoggedUser,
        freshnessPolicy: DataFreshnessPolicy,
        actorSystem: ActorSystem
    ): Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[StatusDetails]]]] = {
      import actorSystem._
      val deploymentStatusesOptFuture
          : Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[StatusDetails]]]] =
        dmDispatcher
          .deploymentManager(processingType)
          .map(
            _.getScenarioDeploymentsStatuses(scenarioName)
              .map(Right(_))
              .recover { case NonFatal(e) => Left(GetDeploymentsStatusesFailure(scenarioName, e)) }
          )
          .getOrElse(Future.successful(Left(ProcessingTypeIsNotConfigured(scenarioName, processingType))))

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

case class ProcessingTypeIsNotConfigured(scenarioName: ProcessName, processingType: ProcessingType)
    extends GetDeploymentsStatusesError(
      s"Cant' get deployments statuses for $scenarioName because processing type: $processingType is not configured",
      null
    )

case class GetDeploymentsStatusesFailure(scenarioName: ProcessName, cause: Throwable)
    extends GetDeploymentsStatusesError(s"Failure during getting deployment statuses for scenario $scenarioName", cause)

case class GetDeploymentsStatusTimeout(scenarioName: ProcessName)
    extends GetDeploymentsStatusesError(s"Timeout during getting deployment statuses for scenario $scenarioName", null)
