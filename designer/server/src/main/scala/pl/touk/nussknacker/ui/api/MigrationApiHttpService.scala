package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.BaseHttpService
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.migrations.MigrationService
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

class MigrationApiHttpService(
    authenticator: AuthenticationResources,
    migrationService: MigrationService
)(implicit val ec: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val remoteEnvironmentApiEndpoints = new MigrationApiEndpoints(authenticator.authenticationMethod())

  expose {
    remoteEnvironmentApiEndpoints.migrateEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogicEitherT { implicit loggedUser => migrateScenarioRequest =>
        EitherT(migrationService.migrate(migrateScenarioRequest))
      }
  }

}
