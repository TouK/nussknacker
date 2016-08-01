package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{Json, PrettyParams}
import de.heikoseeberger.akkahttpargonaut.ArgonautSupport
import pl.touk.esp.ui.core.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.core.process.marshall.DisplayableProcessCodec

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(processById: String => Future[Option[DisplayableProcess]])
                        (implicit ec: ExecutionContext)
  extends Directives with ArgonautSupport {

  implicit val encode = DisplayableProcessCodec.encoder

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  val route = path("process" / Segment) { id =>
    get {
      complete {
        processById(id).map[ToResponseMarshallable] {
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

}
