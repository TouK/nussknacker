package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCode
import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.BaseHttpService
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.MigrateScenarioData.CurrentMigrateScenarioRequest
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.process.migrate.{MigrationApiAdapterError, RemoteEnvironmentCommunicationError}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.util.ApiAdapterServiceError

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
    remoteEnvironmentApiEndpoints.apiVersionEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogic(_ => _ => Future(Right(migrationApiAdapterService.getCurrentApiVersion)))
  }

}
