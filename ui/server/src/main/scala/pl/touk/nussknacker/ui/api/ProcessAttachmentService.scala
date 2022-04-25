package pl.touk.nussknacker.ui.api

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.ProcessAttachmentService.{AttachmentDataWithName, AttachmentToAdd}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.CatsSyntax

import scala.concurrent.{ExecutionContext, Future}

class ProcessAttachmentService(processActivityRepository: ProcessActivityRepository) extends LazyLogging {

  def saveAttachment(processId: ProcessId, processVersionId: VersionId, originalFileName: String, byteSource: Source[ByteString, Any])
                    (implicit ec: ExecutionContext, loggedUser: LoggedUser, mat: Materializer): Future[Unit] = {
    Future {
      byteSource.runWith(StreamConverters.asInputStream())
    }.map(data => AttachmentToAdd(processId, processVersionId, originalFileName, data.readAllBytes()))
      .flatMap(processActivityRepository.addAttachment(_))
  }

  def readAttachment(attachmentId: Long)
                    (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Option[AttachmentDataWithName]] = {
    val attachmentFutureOpt = processActivityRepository.findAttachment(attachmentId)
    CatsSyntax.futureOpt.map(attachmentFutureOpt) { attachment =>
      (attachment.fileName, attachment.data)
    }
  }
}


object ProcessAttachmentService {
  type AttachmentDataWithName = (String, Array[Byte])

  case class AttachmentToAdd(processId: ProcessId,
                             processVersionId: VersionId,
                             fileName: String,
                             data: Array[Byte])
}
