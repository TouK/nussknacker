package pl.touk.nussknacker.ui.process.deployment.deploymentstatus

import cats.effect.IO
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, DeploymentStatusDetails, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.ScenarioIdData
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object DeploymentManagerReliableStatusesWrapper {

  implicit class Ops(dmDispatcher: DeploymentManagerDispatcher) {

    def getScenarioDeploymentsStatusesWithErrorWrappingAndTimeoutOpt(
        scenarioIdData: ScenarioIdData,
        timeoutOpt: Option[FiniteDuration]
    )(
        implicit user: LoggedUser,
        freshnessPolicy: DataFreshnessPolicy,
        executionContextWithIORuntime: ExecutionContextWithIORuntime
    ): Future[Either[GetDeploymentsStatusesError, WithDataFreshnessStatus[List[DeploymentStatusDetails]]]] = {
      import executionContextWithIORuntime.ioRuntime
      val deploymentStatusesOpt = IO.fromFuture(IO {
        dmDispatcher.deploymentManager(scenarioIdData.processingType) match {
          case Some(deploymentManager) =>
            deploymentManager
              .getScenarioDeploymentsStatuses(scenarioIdData.name)
              .map(Right(_))
              .recover { case NonFatal(e) =>
                Left(GetDeploymentsStatusesFailure(scenarioIdData.name, e))
              }
          case None =>
            Future.successful(Left(ProcessingTypeIsNotConfigured(scenarioIdData.name, scenarioIdData.processingType)))
        }
      })

      timeoutOpt
        .map { timeout =>
          deploymentStatusesOpt.timeoutTo(timeout, IO.delay(Left(GetDeploymentsStatusTimeout(scenarioIdData.name))))
        }
        .getOrElse(deploymentStatusesOpt)
        .unsafeToFuture()
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
