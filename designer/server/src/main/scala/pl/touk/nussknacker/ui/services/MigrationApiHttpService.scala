package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioWithDetails}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.{NuDesignerError, UnauthorizedError}
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
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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
          val environmentId                    = migrateScenarioRequest.environmentId
          val parameters: ScenarioParameters   = ???
          val processingType                   = scenarioWithDetailsForMigrations.processingType
          val scenarioGraphUnsafe              = scenarioWithDetailsForMigrations.scenarioGraphUnsafe
          val processName                      = scenarioWithDetailsForMigrations.name
          val isFragment                       = scenarioWithDetailsForMigrations.isFragment
          val forwardedUser                    = if (passUsernameInMigration) Some(loggedUser) else None
          val forwardedUsername                = forwardedUser.map(user => RemoteUserName(user.username))
          val updateProcessComment =
            UpdateProcessComment(s"Scenario migrated from $environmentId by ${loggedUser.username}")
          val updateScenarioCommand =
            UpdateScenarioCommand(scenarioGraphUnsafe, updateProcessComment, forwardedUsername)

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
                case Left(ProcessNotFoundError(processName)) =>
                  Future.successful(
                    createProcess(processName, parameters, isFragment, forwardedUsername, useLegacyCreateScenarioApi)
                  )
                case Right(_) => Future.successful(Right(()))
                case Left(e)  => Future.failed(e)
              }

              canWrite <- processAuthorizer.check(processId, Permission.Write, loggedUser)
              _ <- if (canWrite) Future.successful(Right(())) else Future.failed(new UnauthorizedError(loggedUser))

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

            } yield Right(())
          )
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
