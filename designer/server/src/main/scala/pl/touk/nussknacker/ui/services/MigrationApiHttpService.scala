package pl.touk.nussknacker.ui.services

import akka.http.scaladsl.model.StatusCodes
import cats.data.EitherT
import cats.syntax.traverse._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioWithDetails, ScenarioWithDetailsForMigrations}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.{EnvironmentComparisonResult, MigrationApiEndpoints, ProcessDifference}
import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessService.{FetchScenarioGraph, GetScenarioWithDetailsOptions}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.process.migrate.{
  MigrationToArchivedError,
  MigrationValidationError,
  RemoteEnvironment,
  RemoteEnvironmentCommunicationError
}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.ui.util.EitherTImplicits

import scala.util.{Failure, Success}

class MigrationApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _],
    remoteEnvironment: RemoteEnvironment
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  import EitherTImplicits._

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(authenticator.authenticationMethod())
  private val passUsernameInMigrations      = true

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => migrateScenarioRequest =>
        {
          val scenarioWithDetailsForMigrations = migrateScenarioRequest.scenarioWithDetailsForMigrations
          val environmentId                    = migrateScenarioRequest.environmentId
          val processingType                   = scenarioWithDetailsForMigrations.processingType
          val scenarioGraphUnsafe              = scenarioWithDetailsForMigrations.scenarioGraphUnsafe
          val processName                      = scenarioWithDetailsForMigrations.name
          val isFragment                       = scenarioWithDetailsForMigrations.isFragment
          val remoteUser                       = if (passUsernameInMigrations) Some(loggedUser) else None
          val remoteUsername                   = remoteUser.map(user => RemoteUserName(user.username))
          EitherT(
            for {
              validation <- Future.successful(
                processResolver
                  .forTypeUnsafe(processingType)
                  .validateBeforeUiResolving(scenarioGraphUnsafe, processName, isFragment)
              )

              _ <-
                if (validation.errors != ValidationErrors.success)
                  Future.failed(MigrationValidationError(validation.errors))
                else Future.successful(())

              processId <- processService.getProcessIdUnsafe(processName)
              processIdWithName = ProcessIdWithName(processId, processName)

              remoteScenarioWithDetailsE <- processService
                .getLatestProcessWithDetails(
                  processIdWithName,
                  GetScenarioWithDetailsOptions(
                    FetchScenarioGraph(FetchScenarioGraph.DontValidate),
                    fetchState = true
                  )
                )
                .transformWith[Either[NuDesignerError, ScenarioWithDetails]] {
                  case Failure(e: ProcessNotFoundError) => Future.successful(Left(e))
                  case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                    Future.failed(MigrationToArchivedError(scenarioWithDetails.name, environmentId))
                  case Success(scenarioWithDetails) => Future.successful(Right(scenarioWithDetails))
                  case Failure(e)                   => Future.failed(e)
                }

              _ <- remoteScenarioWithDetailsE match {
                case Left(ProcessNotFoundError(processName)) => ???
                case Right(_)                                => Future.successful(Right(()))
              }

            } yield ???
          )
        }
      }
  }

  /*  expose {
    remoteEnvironmentApiEndpoints.migrateEndpointV2
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => data =>
          EitherT(
                for {
                  validation <- Future.successful(
                    processResolver
                      .forTypeUnsafe()
                      .validateBeforeUiResolving(details.scenarioGraphUnsafe, details.name, details.isFragment)
                  )

                  _ <-
                    if (validation.errors != ValidationErrors.success)
                      Future.failed(MigrationValidationError(validation.errors))
                    else Future.successful(())

                  scenarioWithDetailsE <- processService
                    .getLatestProcessWithDetails(
                      processIdWithName,
                      GetScenarioWithDetailsOptions(
                        FetchScenarioGraph(FetchScenarioGraph.DontValidate),
                        fetchState = true
                      )
                    )
                    .transform[Either[NuDesignerError, ScenarioWithDetails]] {
                      case Failure(e: ProcessNotFoundError) => Success(Left(e))
                      case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                        Failure(MigrationToArchivedError(scenarioWithDetails.name, environmentId))
                      case Success(scenarioWithDetails) => Success(Right(scenarioWithDetails))
                    }

                  _ <- scenarioWithDetailsE match {
                    case Left(ProcessNotFoundError(processName)) => ???
                    case Right(_)              => Future.successful(Right(()))
                  }


                } yield ???
        }

  }*/

}
