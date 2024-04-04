package pl.touk.nussknacker.ui.api.utils

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.UnauthorizedError
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait ScenarioHttpServiceExtensions {

  protected type BusinessErrorType

  protected def scenarioService: ProcessService

  protected implicit def executionContext: ExecutionContext

  protected def noScenarioError(scenarioName: ProcessName): BusinessErrorType

  protected def noPermissionError: BusinessErrorType with CustomAuthorizationError

  protected def getScenarioWithDetailsByName(
      scenarioName: ProcessName
  )(implicit loggedUser: LoggedUser): EitherT[Future, BusinessErrorType, ScenarioWithDetails] =
    for {
      scenarioId <- EitherT.fromOptionF(scenarioService.getProcessId(scenarioName), noScenarioError(scenarioName))
      scenarioDetails <- extractErrors(
        scenarioService.getLatestProcessWithDetails(
          ProcessIdWithName(scenarioId, scenarioName),
          GetScenarioWithDetailsOptions.detailsOnly
        )
      )
    } yield scenarioDetails

  protected def extractErrors[T](
      future: Future[T]
  ): EitherT[Future, BusinessErrorType, T] = {
    EitherT(
      future.transform {
        case Success(result)               => Success(Right(result))
        case Failure(_: UnauthorizedError) => Success(Left(noPermissionError))
        case Failure(ex)                   => Failure(ex)
      }
    )
  }

}
