package pl.touk.nussknacker.ui.api


import java.io.File

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.{ActorAttributes, Materializer}
import akka.stream.scaladsl.FileIO
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.util.{AkkaHttpResponse, CatsSyntax}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ProcessActivityResource(processActivityRepository: ProcessActivityRepository, val processRepository: FetchingProcessRepository)
                             (implicit val ec: ExecutionContext, mat: Materializer) extends Directives with Argonaut62Support with RouteWithUser with ProcessDirectives {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route = {
    path("processes" / Segment / "activity") { processName =>
      (get & processId(processName)) { processId =>
        complete {
          processActivityRepository.findActivity(processId.id, processName)
        }
      }
    } ~ path("processes" / Segment / LongNumber / "activity" / "comments") { (processName, versionId) =>
      (post & processId(processName)) { processId =>
        entity(as[Array[Byte]]) { commentBytes =>
          complete {
            val comment = new String(commentBytes, java.nio.charset.Charset.forName("UTF-8"))
            processActivityRepository.addComment(processId.id, versionId, comment)
          }
        }
      }
    } ~ path("processes" / Segment / "activity" / "comments" / LongNumber) { (processName, commentId) =>
      (delete & processId(processName)) { processId =>
        complete {
          processActivityRepository.deleteComment(commentId)
        }
      }
    }
  }
}

class AttachmentResources(attachmentService: ProcessAttachmentService, val processRepository: FetchingProcessRepository)
                         (implicit val ec: ExecutionContext, mat: Materializer) extends Directives with Argonaut62Support with RouteWithUser with ProcessDirectives {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route = {
    path("processes" / Segment / LongNumber / "activity" / "attachments") { (processName, versionId) =>
      (post & processId(processName)) { processId =>
        fileUpload("attachment") { case (metadata, byteSource) =>
          complete {
            attachmentService.saveAttachment(processId.id, versionId, metadata.fileName, byteSource)
          }
        }
      }
    } ~ path("processes" / Segment / LongNumber / "activity" / "attachments" / LongNumber) { (processName, versionId, attachmentId) =>
      (get & processId(processName)) { processId =>
        extractSettings { settings =>
          complete {
            val attachmentFile = attachmentService.readAttachment(attachmentId)
            CatsSyntax.futureOpt.map(attachmentFile) { case (file, attachment) =>
              AkkaHttpResponse.asFile(fileEntity(settings, file), attachment.fileName)
            }
          }
        }
      }
    }
  }

  private def fileEntity(settings: RoutingSettings, file: File): ResponseEntity = {
    val contentType = ContentTypeResolver.Default(file.getName)
    HttpEntity.Default(contentType, file.length,
      FileIO.fromPath(file.toPath).withAttributes(ActorAttributes.dispatcher(settings.fileIODispatcher)))
  }
}
