package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser, MigrationApiEndpoints}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{
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
    processChangeListener: ProcessChangeListener
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

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
          val forwardedUser                    = if (passUsernameInMigrations) Some(loggedUser) else None
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
                case Left(ProcessNotFoundError(processName)) => ???
                case Right(_)                                => Future.successful(Right(()))
                case Left(e)                                 => Future.failed(e)
              }

              // TODO: Create scenario if not exists

              // TODO: Permission check for updateProcess

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

  /*
      _ <- EitherT {
        saveProcess(
          localGraph,
          processName,
          UpdateProcessComment(s"Scenario migrated from $environmentId by ${loggedUser.username}"),
          usernameToPass
        )
      }


  private def saveProcess(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      comment: UpdateProcessComment,
      forwardedUserName: Option[RemoteUserName]
  )(implicit ec: ExecutionContext): Future[Either[NuDesignerError, ValidationResult]] = {
    for {
      processToSave <- Marshal(UpdateScenarioCommand(scenarioGraph, comment, forwardedUserName))
        .to[MessageEntity](marshaller, ec)
      response <- invokeJson[ValidationResult](
        HttpMethods.PUT,
        List("processes", processName.value),
        requestEntity = processToSave
      )
    } yield response
  }


          } ~ (put & canWrite(processId)) {
            entity(as[UpdateScenarioCommand]) { updateCommand =>
              canOverrideUsername(processId.id, updateCommand.forwardedUserName)(ec, user) {
                complete {
                  processService
                    .updateProcess(processId, updateCommand)
                    .withSideEffect(response =>
                      response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
                    )
                    .map(_.validationResult)
                }
              }
            }
   */
}
