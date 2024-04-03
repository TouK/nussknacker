package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.BaseHttpService
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.{MigrationApiAdapterService, MigrationService}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources

import scala.concurrent.ExecutionContext

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
      .serverLogicEitherT { implicit loggedUser =>
        {
          case migrateScenarioRequestV1_15 @ MigrateScenarioRequestV1_15(_, _, _, _, _, _, _) =>
            EitherT(migrationService.migrate(migrateScenarioRequestV1_15))
          case migrateScenarioRequestOld @ MigrateScenarioRequestV1_14(_, _, _, _, _, _, _) =>
            val migrateScenarioRequestNew = migrationApiAdapterService.adaptToHighestVersion(migrateScenarioRequestOld)
            EitherT(migrationService.migrate(migrateScenarioRequestNew))
        }
      }
  }

}
