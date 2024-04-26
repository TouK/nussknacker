package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationError
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.{ExecutionContext, Future}

class MigrationApiHttpService(
    authenticator: AuthenticationResources,
    migrationService: MigrationService,
    migrationApiAdapterService: MigrationApiAdapterService
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(authenticator.authenticationMethod())

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[MigrationError])
      .serverLogicEitherT { implicit loggedUser => req: MigrateScenarioRequestDto =>
        EitherT
          .fromEither[Future](MigrateScenarioData.toDomain(req))
          .map(migrationService.migrate(_))
      }
  }

  expose {
    remoteEnvironmentApiEndpoints.scenarioDescriptionVersionEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogic(_ => _ => Future(Right(ApiVersion(migrationApiAdapterService.getCurrentApiVersion))))
  }

}
