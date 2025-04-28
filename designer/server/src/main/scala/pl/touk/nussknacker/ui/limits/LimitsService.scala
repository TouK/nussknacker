package pl.touk.nussknacker.ui.limits

import cats.data.EitherT
import cats.effect.IO
import cats.effect.std.Mutex
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.ActiveScenariosLimitExceededError
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

class LimitsService(
    globalLimitsConfig: GlobalLimitsConfig,
    activeScenariosLimitProvider: ProcessingTypeDataProvider[LimitsConfig, _],
    scenarioStatusProvider: ScenarioStatusProvider
) extends LazyLogging {

  def checkScenarioLimitsBeforeDeploymentUnsafe(
      deployingScenario: ProcessName,
      scenarioProcessingType: ProcessingType
  )(implicit user: LoggedUser): IO[Either[LimitError, Unit]] = {
    checkAllLimits(deployingScenario, scenarioProcessingType).value
  }

  def checkScenarioLimitsBeforeDeployment[ACTION_RESULT](
      deployingScenario: ProcessName,
      scenarioProcessingType: ProcessingType
  )(withinLimitsAction: IO[ACTION_RESULT])(implicit user: LoggedUser): IO[Either[LimitError, ACTION_RESULT]] = {
    limitsServiceLock.surround {
      val result = for {
        _      <- checkAllLimits(deployingScenario, scenarioProcessingType)
        result <- EitherT.right[LimitError](withinLimitsAction)
      } yield result
      result.value
    }
  }

  private def checkAllLimits(
      deployingScenario: ProcessName,
      scenarioProcessingType: ProcessingType
  )(implicit user: LoggedUser) = {
    for {
      _ <- checkPerProcessingTypeLimits(deployingScenario, scenarioProcessingType)
      _ <- checkGlobalLimits(deployingScenario)
    } yield ()
  }

  private def limitsServiceLock = {
    // note: currently we use in memory lock. It exposes Resource[Io, Unit] interface. It can be easily implemented
    //       e.g. as a Postgresql-based lock. We may want to do this when the want to have Designer deployed in HA
    LimitsService.mutex.lock
  }

  private def checkPerProcessingTypeLimits(
      deployingScenario: ProcessName,
      scenarioProcessingType: ProcessingType
  )(implicit user: LoggedUser): EitherT[IO, ActiveScenariosLimitExceededError, Unit] = {
    activeScenariosLimitProvider.forProcessingType(scenarioProcessingType) match {
      case Some(LimitsConfig(Some(activeScenariosLimit))) =>
        EitherT {
          scenarioStatusProvider
            .getActiveScenariosFor(scenarioProcessingType)
            .map { currentlyActiveScenarios =>
              checkCurrentlyActiveScenariosLimit(
                deployingScenario,
                currentlyActiveScenarios,
                activeScenariosLimit.value
              )
            }
        }
      case Some(LimitsConfig(None)) | None =>
        EitherT.right(IO.unit)
    }
  }

  private def checkGlobalLimits(
      deployingScenario: ProcessName
  ): EitherT[IO, ActiveScenariosLimitExceededError, Unit] = {
    globalLimitsConfig.activeScenariosLimit match {
      case Some(activeScenariosLimit) =>
        EitherT {
          implicit val user: LoggedUser = NussknackerInternalUser.instance
          val allProcessingTypes        = activeScenariosLimitProvider.all.keys
          scenarioStatusProvider
            .getActiveScenariosFor(allProcessingTypes)
            .map { currentlyActiveScenarios =>
              checkCurrentlyActiveScenariosLimit(
                deployingScenario,
                currentlyActiveScenarios,
                activeScenariosLimit.value
              )
            }
        }
      case None =>
        EitherT.right(IO.unit)
    }
  }

  private def checkCurrentlyActiveScenariosLimit(
      currentlyDeployingScenario: ProcessName,
      currentlyActiveScenarios: Set[ProcessName],
      activeScenariosLimit: Int
  ) = {
    if (currentlyActiveScenarios.contains(currentlyDeployingScenario)) {
      Right(())
    } else if (currentlyActiveScenarios.size + 1 <= activeScenariosLimit) {
      Right(())
    } else {
      logger.debug(s"""Active scenarios limit ($activeScenariosLimit) exceeded.
           |Active scenarios: ${currentlyActiveScenarios.map(_.value).mkString(", ")}.
           |Scenario is being deployed: $currentlyDeployingScenario.
           |""".stripMargin)
      Left(ActiveScenariosLimitExceededError(activeScenariosLimit))
    }
  }

}

object LimitsService {

  sealed trait LimitError

  object LimitError {

    final case class ActiveScenariosLimitExceededError(activeScenariosLimit: Int)
        extends IllegalArgumentException(
          s"The limit of active scenarios has been reached. You can have a maximum of $activeScenariosLimit active scenarios."
        )
        with LimitError

  }

  private val mutex: Mutex[IO] = {
    implicit val ioRuntime: IORuntime = IORuntime.global
    Mutex[IO].unsafeRunSync()
  }

}
