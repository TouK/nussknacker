package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.EncodeJson
import argonaut.ArgonautShapeless._
import argonaut.Argonaut._
import argonaut._

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._

import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{MigratorCommunicationError, ProcessMigrator, TestMigrationResult}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class MigrationResources(migrator: ProcessMigrator,
                         processRepository: ProcessRepository)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._
  import argonaut.Argonaut._

  private implicit val differenceCodec = ProcessComparator.codec

  private implicit val map = EncodeJson.derive[TestMigrationResult]
  private implicit val encodeResults = EncodeJson.derive[TestMigrationSummary]

  def route(implicit user: LoggedUser) : Route = {
    authorize(user.hasPermission(Permission.Deploy)) {

      pathPrefix("migration") {
          path("compare" / Segment) { processId =>
            get {
              complete {
                withProcess(processId, (process) => migrator.compare(process))
              }
            }
          } ~
          path("migrate" / Segment) { processId =>
            post {
              complete {
                withProcess(processId, (process) => migrator.migrate(process))
              }
            }
          } ~
          path("testMigration") {
            get {
              complete {
                migrator.testMigration
                  .flatMap(_.fold((Future.successful[HttpResponse] _)
                    .compose(EspErrorToHttp.espErrorToHttp), testMigrationResponse))
              }
            }
          }
      }
    }
  }



  private def testMigrationResponse(testMigrationResults: List[TestMigrationResult]) : Future[HttpResponse] = {
    val failedMigrations = testMigrationResults.filter(_.shouldFail).map(_.converted.id)
    val (status, message) = failedMigrations match {
      case Nil => (StatusCodes.OK, "Migrations successful")
      case _ => (StatusCodes.InternalServerError,
        s"Migration failed, following processes have new errors: ${failedMigrations.mkString(", ")}")
    }
    val summary = TestMigrationSummary(message, testMigrationResults)

    Marshal(summary).to[MessageEntity].map(e => HttpResponse(status = status, entity = e))
  }

  private def withProcess[T:EncodeJson](processId: String, fun: (DisplayableProcess) => Future[Either[EspError, T]])(implicit user: LoggedUser) = {
    processRepository.fetchLatestProcessDetailsForProcessId(processId).map {
      _.flatMap(_.json)
    }.flatMap {
      case Some(dispProcess) => fun(dispProcess)
      case None => Future.successful(Left(ProcessNotFoundError(processId)))
    }.map(EspErrorToHttp.toResponseEither[T])
  }


}

case class TestMigrationSummary(message: String, testMigrationResults: List[TestMigrationResult])
