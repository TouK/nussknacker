package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationError
import pl.touk.nussknacker.ui.security.api.AuthManager

import scala.concurrent.{ExecutionContext, Future}

class MigrationApiHttpService(
    authManager: AuthManager,
    migrationService: MigrationService,
    migrationApiAdapterService: MigrationApiAdapterService
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(
    authManager.authenticationEndpointInput()
  )

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[MigrationError])
      .serverLogicEitherT { implicit loggedUser => req: MigrateScenarioRequestDto =>
        EitherT
          .fromEither[Future](MigrateScenarioData.toDomain(req))
          .flatMapF(migrationService.migrate(_))
      }
  }

  expose {
    remoteEnvironmentApiEndpoints.scenarioDescriptionVersionEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic(_ => _ => Future(Right(ApiVersion(migrationApiAdapterService.getCurrentApiVersion))))
  }

}
