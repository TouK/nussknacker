package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.{NuDesignerError, UnauthorizedError}
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser, MigrationApiEndpoints}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.migrations.MigrationService
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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MigrationApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    migrationService: MigrationService
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(authenticator.authenticationMethod())
  private val passUsernameInMigration       = true

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => migrateScenarioRequest =>
        EitherT(migrationService.migrate(migrateScenarioRequest))
      }
  }

}
