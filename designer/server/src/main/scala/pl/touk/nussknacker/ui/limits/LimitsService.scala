package pl.touk.nussknacker.ui.limits

import cats.data.EitherT
import cats.effect.IO
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.ActiveScenariosLimitExceededError
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

class LimitsService(
    globalLimitsConfig: GlobalLimitsConfig,
    activeScenariosLimitProvider: ProcessingTypeDataProvider[LimitsConfig, _],
    scenarioStatusProvider: ScenarioStatusProvider
) {

  // todo: check locking

  def checkScenarioLimitsBeforeDeployment(
      scenarioProcessingType: ProcessingType
  )(implicit user: LoggedUser): IO[Either[LimitError, Unit]] = {
    val result = for {
      _ <- checkPerProcessingTypeLimits(scenarioProcessingType)
      _ <- checkGlobalLimits()
    } yield ()
    result.value
  }

  private def checkPerProcessingTypeLimits(
      scenarioProcessingType: ProcessingType
  )(implicit user: LoggedUser): EitherT[IO, ActiveScenariosLimitExceededError, Unit] = {
    activeScenariosLimitProvider.forProcessingType(scenarioProcessingType) match {
      case Some(LimitsConfig(Some(activeScenariosLimit))) =>
        EitherT {
          scenarioStatusProvider
            .getActiveScenariosCountFor(scenarioProcessingType)
            .map { activeScenariosCount =>
              Either.cond(
                test = activeScenariosCount < activeScenariosLimit.value,
                right = (),
                left = ActiveScenariosLimitExceededError(activeScenariosLimit.value)
              )
            }
        }
      case Some(LimitsConfig(None)) | None =>
        EitherT.right(IO.unit)
    }
  }

  private def checkGlobalLimits(): EitherT[IO, ActiveScenariosLimitExceededError, Unit] = ???
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

}
