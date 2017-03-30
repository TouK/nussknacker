package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.EncodeJson
import pl.touk.esp.ui.EspError
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.migrate.ProcessMigrator
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class MigrationResources(migratorO: Option[ProcessMigrator], processRepository: ProcessRepository)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  def route(user: LoggedUser) : Route = {
    implicit val iu = user
    authorize(user.hasPermission(Permission.Deploy)) {

      pathPrefix("migration") {
        path("settings") {
          get {
            complete {
              MigrationSettings(migratorO.isDefined, targetEnvironmentId = migratorO.map(_.targetEnvironmentId))
            }
          }
        } ~
          path("compare" / Segment) { processId =>
            get {
              complete {
                withProcess(processId, (migrator, process) => migrator.compare(process))
              }
            }
          } ~
          path("migrate" / Segment) { processId =>
            post {
              complete {
                withProcess(processId, (migrator, process) => migrator.migrate(process))
              }
            }
          }
      }
    }
  }

  private def withProcess[T:EncodeJson](processId: String, fun: (ProcessMigrator, DisplayableProcess) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    applyWhenEnabled { migrator =>
      processRepository.fetchLatestProcessDetailsForProcessId(processId).map {
        _.flatMap(_.json)
      }.flatMap {
        case Some(dispProcess) => fun(migrator, dispProcess)
        case None => Future.successful(Left(ProcessNotFoundError(processId)))
      }.map(EspErrorToHttp.toResponseEither[T])
    }
  }

  private def applyWhenEnabled(fun: ProcessMigrator => ToResponseMarshallable) : ToResponseMarshallable = migratorO match {
    case Some(migrator) => fun(migrator)
    case None => HttpResponse(status = StatusCodes.BadRequest, entity = "Migrator not enabled")

  }


}

case class MigrationSettings(enabled: Boolean, targetEnvironmentId: Option[String])
