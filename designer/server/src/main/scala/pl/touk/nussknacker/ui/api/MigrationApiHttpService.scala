package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.{MigrationApiAdapterService, MigrationService}
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
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => req: MigrateScenarioRequestDto =>
        migrationService.migrate(req)
      }
  }

  expose {
    remoteEnvironmentApiEndpoints.scenarioDescriptionVersionEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogic(_ => _ => Future(Right(migrationApiAdapterService.getCurrentApiVersion)))
  }

}
