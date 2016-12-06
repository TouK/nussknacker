package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.stream.Materializer
import pl.touk.esp.ui.process.repository.ProcessActivityRepository
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class ProcessActivityResource(processActivityRepository: ProcessActivityRepository)
                             (implicit ec: ExecutionContext, mat: Materializer) extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route = {
    path("processes" / Segment / "activity") { processId =>
      get {
        complete {
          processActivityRepository.findActivity(processId)
        }
      }
    } ~ path("processes" / Segment / LongNumber / "activity" / "comments") { (processId, versionId) =>
      post {
        entity(as[Array[Byte]]) { commentBytes =>
          complete {
            val comment = new String(commentBytes, java.nio.charset.Charset.forName("UTF-8"))
            if (!comment.isEmpty) {
              processActivityRepository.addComment(processId, versionId, comment)
            } else {
              HttpResponse(status = StatusCodes.OK)
            }
          }
        }
      }
    }
  }
}