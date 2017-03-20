package pl.touk.esp.ui.api


import java.io.File

import akka.http.scaladsl.model.HttpEntity.Default
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.stream.{ActorAttributes, Materializer}
import akka.stream.scaladsl.FileIO
import pl.touk.esp.ui.process.repository.ProcessActivityRepository
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.{AkkaHttpResponse, Argonaut62Support, CatsSyntax}

import scala.concurrent.ExecutionContext

class ProcessActivityResource(processActivityRepository: ProcessActivityRepository,
                              attachmentService: ProcessAttachmentService)
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
            processActivityRepository.addComment(processId, versionId, comment)
          }
        }
      }
    } ~ path("processes" / Segment / "activity" / "comments" / LongNumber) { (processId, commentId) =>
      delete {
        complete {
          processActivityRepository.deleteComment(commentId)
        }
      }
    } ~ path("processes" / Segment / LongNumber / "activity" / "attachments") { (processId, versionId) =>
      post {
        fileUpload("attachment") { case (metadata, byteSource) =>
          complete {
            attachmentService.saveAttachment(processId, versionId, metadata.fileName, byteSource)
          }
        }
      }
    } ~ path("processes" / Segment / LongNumber / "activity" / "attachments" / LongNumber) { (processId, versionId, attachmentId) =>
      get {
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
      FileIO.fromFile(file).withAttributes(ActorAttributes.dispatcher(settings.fileIODispatcher)))
  }
}