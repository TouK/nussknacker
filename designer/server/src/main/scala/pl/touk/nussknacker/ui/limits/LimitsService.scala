package pl.touk.nussknacker.ui.limits

import cats.data.EitherT
import cats.effect.IO
import cats.effect.std.Mutex
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.{
  DontReplaceDeployment,
  ReplaceDeploymentWithSameScenarioName
}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.MaxActiveScenariosCountExceededError
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.{
  ScenarioStatusProvider => OldDeploymentsApproachScenarioStatusProvider
}
import pl.touk.nussknacker.ui.process.newdeployment.{
  ScenarioStatusProvider => NewDeploymentsApproachScenarioStatusProvider
}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

class LimitsService(
    globalLimitsConfig: GlobalLimitsConfig,
    perProcessingTypesLimitsProvider: ProcessingTypeDataProvider[LimitsConfig, _],
    oldDeploymentsApproachScenarioStatusProvider: OldDeploymentsApproachScenarioStatusProvider,
    newDeploymentsApproachScenarioStatusProvider: NewDeploymentsApproachScenarioStatusProvider
) extends LazyLogging {

  def checkActiveScenarioLimitsBeforeDeployment[ACTION_RESULT](
      deployingScenario: ProcessName,
      deployingScenarioProcessingType: ProcessingType,
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  )(withinLimitsAction: IO[ACTION_RESULT])(implicit user: LoggedUser): IO[Either[LimitError, ACTION_RESULT]] = {
    limitsServiceLock.surround {
      val result = for {
        _      <- checkAllLimits(deployingScenario, deployingScenarioProcessingType, deploymentUpdateStrategy)
        result <- EitherT.right[LimitError](withinLimitsAction)
      } yield result
      result.value
    }
  }

  private def checkAllLimits(
      deployingScenario: ProcessName,
      deployingScenarioProcessingType: ProcessingType,
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  )(implicit user: LoggedUser) = {
    for {
      _ <- checkPerProcessingTypeLimits(deployingScenario, deployingScenarioProcessingType, deploymentUpdateStrategy)
      _ <- checkGlobalLimits(deployingScenario, deploymentUpdateStrategy)
    } yield ()
  }

  private def limitsServiceLock = {
    // note: currently we use in memory lock. It exposes Resource[Io, Unit] interface. It can be easily implemented
    //       e.g. as a Postgresql-based lock. We may want to do this when the want to have Designer deployed in HA
    LimitsService.mutex.lock
  }

  private def checkPerProcessingTypeLimits(
      deployingScenario: ProcessName,
      deployingScenarioProcessingType: ProcessingType,
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  )(implicit user: LoggedUser): EitherT[IO, MaxActiveScenariosCountExceededError, Unit] = {
    perProcessingTypesLimitsProvider.forProcessingType(deployingScenarioProcessingType) match {
      case Some(LimitsConfig(Some(maxActiveScenariosCount))) =>
        EitherT {
          getActiveScenariosFor(deployingScenarioProcessingType :: Nil)
            .map { currentlyActiveScenarios =>
              checkCurrentlyActiveScenariosCount(
                deployingScenario,
                currentlyActiveScenarios,
                maxActiveScenariosCount.value,
                deploymentUpdateStrategy
              )
            }
        }
      case Some(LimitsConfig(None)) | None =>
        EitherT.right(IO.unit)
    }
  }

  private def checkGlobalLimits(
      deployingScenario: ProcessName,
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  ): EitherT[IO, MaxActiveScenariosCountExceededError, Unit] = {
    globalLimitsConfig.maxActiveScenariosCount match {
      case Some(maxActiveScenariosCount) =>
        EitherT {
          implicit val user: LoggedUser = NussknackerInternalUser.instance
          val allProcessingTypes        = perProcessingTypesLimitsProvider.all.keys
          getActiveScenariosFor(allProcessingTypes)
            .map { currentlyActiveScenarios =>
              checkCurrentlyActiveScenariosCount(
                deployingScenario,
                currentlyActiveScenarios,
                maxActiveScenariosCount.value,
                deploymentUpdateStrategy
              )
            }
        }
      case None =>
        EitherT.right(IO.unit)
    }
  }

  private def checkCurrentlyActiveScenariosCount(
      currentlyDeployingScenario: ProcessName,
      currentlyActiveScenarios: Set[ProcessName],
      maxActiveScenariosCount: Int,
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  ) = {
    val maxActiveScenariosIncludingCurrentlyDeployingScenario = deploymentUpdateStrategy match {
      case ReplaceDeploymentWithSameScenarioName(_) if currentlyActiveScenarios.contains(currentlyDeployingScenario) =>
        maxActiveScenariosCount + 1
      case ReplaceDeploymentWithSameScenarioName(_) | DontReplaceDeployment =>
        maxActiveScenariosCount
    }
    if (currentlyActiveScenarios.size + 1 <= maxActiveScenariosIncludingCurrentlyDeployingScenario) {
      Right(())
    } else {
      logger.debug(s"""Active scenarios limit ($maxActiveScenariosCount) exceeded.
           |Active scenarios: ${currentlyActiveScenarios.map(_.value).mkString(", ")}.
           |Scenario is being deployed: $currentlyDeployingScenario.
           |""".stripMargin)
      Left(MaxActiveScenariosCountExceededError(maxActiveScenariosCount))
    }
  }

  private def getActiveScenariosFor(
      processingTypes: Iterable[ProcessingType]
  )(implicit user: LoggedUser): IO[Set[ProcessName]] = {
    IO
      .both(
        oldDeploymentsApproachScenarioStatusProvider.getActiveScenariosFor(processingTypes),
        newDeploymentsApproachScenarioStatusProvider.getActiveScenariosFor(processingTypes),
      )
      .map { case (oldDeploymentSolutionScenarios, newDeploymentSolutionScenarios) =>
        oldDeploymentSolutionScenarios ++ newDeploymentSolutionScenarios
      }
  }

}

object LimitsService {

  sealed trait LimitError

  object LimitError {

    final case class MaxActiveScenariosCountExceededError(maxActiveScenariosCount: Int)
        extends IllegalArgumentException(
          s"The limit of active scenarios has been reached. You can have a maximum of $maxActiveScenariosCount active scenarios."
        )
        with LimitError

  }

  private val mutex: Mutex[IO] = {
    implicit val ioRuntime: IORuntime = IORuntime.global
    Mutex[IO].unsafeRunSync()
  }

}
