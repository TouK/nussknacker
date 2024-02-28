package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser, MigrationApiEndpoints}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{
  CreateScenarioCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  UpdateScenarioCommand
}
import pl.touk.nussknacker.ui.process.migrate.{MigrationToArchivedError, MigrationValidationError}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.FatalValidationError
import pl.touk.nussknacker.ui.{NuDesignerError, UnauthorizedError}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MigrationApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _],
    processAuthorizer: AuthorizeProcess,
    processChangeListener: ProcessChangeListener,
    useLegacyCreateScenarioApi: Boolean
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(authenticator.authenticationMethod())
  private val passUsernameInMigration       = true

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => migrateScenarioRequest =>
        {
          val scenarioWithDetailsForMigrations = migrateScenarioRequest.scenarioWithDetailsForMigrations
          val sourceEnvironmentId              = migrateScenarioRequest.sourceEnvironmentId
          val targetEnvironmentId              = config.getString("environment")
          val processingMode                   = migrateScenarioRequest.processingMode
          val processCategory                  = scenarioWithDetailsForMigrations.processCategory
          val engineSetupName                  = migrateScenarioRequest.engineSetupName
          val parameters                       = ScenarioParameters(processingMode, processCategory, engineSetupName)
          val processingType                   = scenarioWithDetailsForMigrations.processingType
          val scenarioGraphUnsafe              = scenarioWithDetailsForMigrations.scenarioGraphUnsafe
          val processName                      = scenarioWithDetailsForMigrations.name
          val isFragment                       = scenarioWithDetailsForMigrations.isFragment
          val forwardedUser                    = if (passUsernameInMigration) Some(loggedUser) else None
          val forwardedUsername                = forwardedUser.map(user => RemoteUserName(user.username))
          val updateProcessComment =
            UpdateProcessComment(s"Scenario migrated from $sourceEnvironmentId by ${loggedUser.username}")
          val updateScenarioCommand =
            UpdateScenarioCommand(scenarioGraphUnsafe, updateProcessComment, forwardedUsername)

          val future: Future[Unit] = for {
            validation <- Future.successful(
              FatalValidationError.renderNotAllowedAsError(
                processResolver
                  .forTypeUnsafe(processingType)
                  .validateBeforeUiResolving(scenarioGraphUnsafe, processName, isFragment)
              )
            )

            _ <- validation match {
              case Left(e) => Future.failed(e)
              case Right(validationResults) =>
                if (validationResults.errors != ValidationErrors.success)
                  Future.failed(MigrationValidationError(validationResults.errors))
                else Future.successful(())
            }

            processIdO <- processService.getProcessId(processName)

            _ <- processIdO match {
              case Some(pid) =>
                val processIdWithName = ProcessIdWithName(pid, processName)
                processService
                  .getLatestProcessWithDetails(
                    processIdWithName,
                    GetScenarioWithDetailsOptions(
                      FetchScenarioGraph(FetchScenarioGraph.DontValidate),
                      fetchState = true
                    )
                  )
                  .transformWith[Unit] {
                    case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                      Future
                        .failed(MigrationToArchivedError(scenarioWithDetails.name, targetEnvironmentId))
                    case Success(_) => Future.successful(())
                    case Failure(e) => Future.failed(e)
                  }
              case None =>
                createProcess(processName, parameters, isFragment, forwardedUsername, useLegacyCreateScenarioApi)
            }

            processId <- processService.getProcessIdUnsafe(processName)
            processIdWithName = ProcessIdWithName(processId, processName)

            canWrite <- processAuthorizer.check(processId, Permission.Write, loggedUser)
            _        <- if (canWrite) Future.successful(()) else Future.failed(new UnauthorizedError(loggedUser))

            canOverrideUsername <- processAuthorizer
              .check(processId, Permission.OverrideUsername, loggedUser)
              .map(_ || forwardedUsername.isEmpty)
            _ <-
              if (canOverrideUsername) Future.successful(Right(()))
              else Future.failed(new UnauthorizedError(loggedUser))

            _ <- processService
              .updateProcess(processIdWithName, updateScenarioCommand)
              .withSideEffect(response =>
                response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
              )
              .map(_.validationResult)

          } yield ()

          val transformedFuture: Future[Either[NuDesignerError, Unit]] =
            future.transform[Either[NuDesignerError, Unit]] { (t: Try[Unit]) =>
              t match {
                case Failure(e: NuDesignerError) => Success(Left(e))
                case Failure(e: Throwable)       => Failure(e)
                case Success(())                 => Success(Right(()))
              }
            }

          EitherT(transformedFuture)
        }
      }
  }

  private def notifyListener(event: ProcessChangeEvent)(implicit user: LoggedUser): Unit = {
    implicit val listenerUser: User = ListenerApiUser(user)
    processChangeListener.handle(event)
  }

  private def createProcess(
      processName: ProcessName,
      parameters: ScenarioParameters,
      isFragment: Boolean,
      forwardedUsername: Option[RemoteUserName],
      useLegacyCreateScenarioApi: Boolean
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = if (useLegacyCreateScenarioApi) {
    processService
      .createProcess(
        CreateScenarioCommand(processName, Some(parameters.category), None, None, isFragment, forwardedUsername)
      )
      .map(_.toEither)
      .map {
        case Left(value) => Left(value)
        case Right(response) =>
          notifyListener(OnSaved(response.id, response.versionId))
          Right(())
      }
  } else {
    val createScenarioCommand = CreateScenarioCommand(
      processName,
      Some(parameters.category),
      Some(parameters.processingMode),
      Some(parameters.engineSetupName),
      isFragment = isFragment,
      forwardedUserName = forwardedUsername
    )
    processService
      .createProcess(createScenarioCommand)
      .map(_.toEither)
      .map {
        case Left(value) => Left(value)
        case Right(response) =>
          notifyListener(OnSaved(response.id, response.versionId))
          Right(())
      }
  }

}
