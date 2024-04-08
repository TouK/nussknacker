package pl.touk.nussknacker.ui.services

import akka.http.scaladsl.model.StatusCode
import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.BaseHttpService
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.migrations.MigrateScenarioRequest.CurrentMigrateScenarioRequest
import pl.touk.nussknacker.ui.migrations.{
  MigrateScenarioRequest,
  MigrateScenarioRequestV1,
  MigrateScenarioRequestV2,
  MigrationApiAdapterService,
  MigrationService
}
import pl.touk.nussknacker.ui.process.migrate.{
  MigrationApiAdapterError,
  MissingScenarioGraphError,
  RemoteEnvironmentCommunicationError
}
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
      .serverLogicEitherT { implicit loggedUser =>
        { req: MigrateScenarioRequestDto =>
          val migrateScenarioRequest = MigrateScenarioRequest.toDomain(req)

          val localApiVersion    = migrationApiAdapterService.getCurrentApiVersion
          val remoteApiVersion   = migrateScenarioRequest.currentVersion()
          val versionsDifference = localApiVersion - remoteApiVersion

          val liftedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioRequest] =
            if (versionsDifference > 0) {
              migrationApiAdapterService.adaptUp(migrateScenarioRequest, versionsDifference)
            } else Right(migrateScenarioRequest)

          liftedMigrateScenarioRequestE match {
            case Left(apiAdapterServiceError) =>
              EitherT(
                Future[Either[NuDesignerError, Unit]](
                  Left(
                    MigrationApiAdapterError(apiAdapterServiceError)
                  )
                )
              )
            case Right(liftedMigrateScenarioRequest) =>
              liftedMigrateScenarioRequest match {
                case v2: CurrentMigrateScenarioRequest =>
                  EitherT(migrationService.migrate(v2))
                case _ =>
                  EitherT(
                    Future[Either[NuDesignerError, Unit]](
                      Left(
                        RemoteEnvironmentCommunicationError(
                          StatusCode.int2StatusCode(500),
                          "Migration API adapter service lifted up remote migration request not to its newest local version"
                        )
                      )
                    )
                  )
              }
          }

        }
      }
  }

  expose {
    remoteEnvironmentApiEndpoints.apiVersionEndpoint
      .serverSecurityLogic(authorizeKnownUser[NuDesignerError])
      .serverLogic(_ => _ => Future(Right(migrationApiAdapterService.getCurrentApiVersion)))
  }

}
