package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.repository.{ProcessActivityRepository, UserComment}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{AkkaHttpResponse, CatsSyntax, NuPathMatchers}

import java.io.ByteArrayInputStream
import scala.concurrent.ExecutionContext

class ProcessActivityResource(
    processActivityRepository: ProcessActivityRepository,
    protected val processService: ProcessService,
    val processAuthorizer: AuthorizeProcess
)(implicit val ec: ExecutionContext, mat: Materializer)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives
    with AuthorizeProcessDirectives
    with NuPathMatchers {

  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller

  def securedRoute(implicit user: LoggedUser): Route = {
    path("processes" / ProcessNameSegment / "activity") { processName =>
      (get & processId(processName)) { processId =>
        complete {
          processActivityRepository.findActivity(processId.id)
        }
      }
    } ~ path("processes" / ProcessNameSegment / VersionIdSegment / "activity" / "comments") {
      (processName, versionId) =>
        (post & processId(processName)) { processId =>
          canWrite(processId) {
            entity(as[Array[Byte]]) { commentBytes =>
              complete {
                val comment = UserComment(new String(commentBytes, java.nio.charset.StandardCharsets.UTF_8))
                processActivityRepository.addComment(processId.id, versionId, comment)
              }
            }
          }
        }
    } ~ path("processes" / ProcessNameSegment / "activity" / "comments" / LongNumber) { (processName, commentId) =>
      (delete & processId(processName)) { processId =>
        canWrite(processId) {
          complete {
            processActivityRepository.deleteComment(commentId)
          }
        }
      }
    }
  }

}

class AttachmentResources(
    attachmentService: ProcessAttachmentService,
    protected val processService: ProcessService,
    val processAuthorizer: AuthorizeProcess
)(implicit val ec: ExecutionContext, mat: Materializer)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives
    with AuthorizeProcessDirectives
    with NuPathMatchers {

  def securedRoute(implicit user: LoggedUser): Route = {
    path("processes" / ProcessNameSegment / VersionIdSegment / "activity" / "attachments") { (processName, versionId) =>
      (post & processId(processName)) { processId =>
        canWrite(processId) {
          withoutSizeLimit { // we have separate size limit validation inside attachmentService
            fileUpload("attachment") { case (metadata, byteSource) =>
              complete {
                attachmentService.saveAttachment(processId.id, versionId, metadata.fileName, byteSource)
              }
            }
          }
        }
      }
    } ~ path("processes" / ProcessNameSegment / "activity" / "attachments" / LongNumber) {
      (processName, attachmentId) =>
        (get & processId(processName)) { _ =>
          complete {
            CatsSyntax.futureOpt.map(attachmentService.readAttachment(attachmentId)) { case (name, data) =>
              AkkaHttpResponse.asFile(fileEntity(name, data), name)
            }
          }
        }
    }
  }

  private def fileEntity(name: String, data: Array[Byte]): ResponseEntity = {
    val contentType = ContentTypeResolver.Default(name)
    val inputStream = new ByteArrayInputStream(data)
    HttpEntity.CloseDelimited(contentType, StreamConverters.fromInputStream(() => inputStream))
  }

}
