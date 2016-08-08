package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import cats.data.Validated.{Invalid, Valid}
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._

import scala.concurrent.ExecutionContext

class ProcessesResources(repository: ProcessRepository)
                        (implicit ec: ExecutionContext)
  extends Directives with ArgonautSupport {

  import argonaut.ArgonautShapeless._

  implicit val processEncode = EncodeJson.of[ProcessDetails]

  implicit val processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit val displayableProcessEncode = DisplayableProcessCodec.encoder

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  val route =
    path("processes") {
      get {
        complete {
          repository.fetchProcessesDetails()
        }
      }
    } ~ path("processes" / Segment) { id =>
      get {
        complete {
          repository.fetchProcessDetailsById(id).map[ToResponseMarshallable] {
            case Some(process) =>
              process
            case None =>
              HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              )
          }
        }
      }
    } ~ path("processes" / Segment / "json") { id =>
      get {
        complete {
          val optionalDisplayableJsonFuture = repository.fetchProcessJsonById(id).map { optionalJson =>
            optionalJson.map(convertToDisplayable)
          }
          optionalDisplayableJsonFuture.map[ToResponseMarshallable] {
            case Some(process) =>
              process
            case None =>
              HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              )
          }
        }
      }
    }

  private def convertToDisplayable(canonicalJson: String): DisplayableProcess = {
    ProcessMarshaller.decode(canonicalJson) match {
      case Valid(canonical) => ProcessConverter.toDisplayable(canonical)
      case Invalid(err) => throw new IllegalArgumentException(err.msg)
    }
  }

}