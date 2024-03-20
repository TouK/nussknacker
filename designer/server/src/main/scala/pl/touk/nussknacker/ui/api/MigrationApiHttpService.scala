package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.{BaseHttpService, MigrationApiEndpoints}
import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos._
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
          case migrateScenarioRequestV2 @ MigrateScenarioRequestV2(_, _, _, _, _, _, _) =>
            EitherT(migrationService.migrate(migrateScenarioRequestV2))
          case migrateScenarioRequestV1 @ MigrateScenarioRequestV1(_, _, _, _, _, _, _) =>
            val migrateScenarioRequestV2 = migrationApiAdapterService.adaptToHigherVersion(migrateScenarioRequestV1)
            EitherT(migrationService.migrate(migrateScenarioRequestV2))
        }
      }
  }

}
