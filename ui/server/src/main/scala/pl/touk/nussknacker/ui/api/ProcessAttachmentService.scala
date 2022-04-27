package pl.touk.nussknacker.ui.api

import akka.stream.{Materializer, StreamLimitReachedException}
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.ProcessAttachmentService.{AttachmentDataWithName, AttachmentToAdd}
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.CatsSyntax

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try, Using}

class ProcessAttachmentService(config: AttachmentsConfig, processActivityRepository: ProcessActivityRepository) extends LazyLogging {

  def saveAttachment(processId: ProcessId, processVersionId: VersionId, originalFileName: String, byteSource: Source[ByteString, Any])
                    (implicit ec: ExecutionContext, loggedUser: LoggedUser, mat: Materializer): Future[Unit] = {

    val inputStream = byteSource
      .limitWeighted(config.maxSizeInBytes)(_.size)
      .runWith(StreamConverters.asInputStream())

    Try(Using.resource(inputStream)(_.readAllBytes())) match {
      case Success(data) => processActivityRepository.addAttachment(AttachmentToAdd(processId, processVersionId, originalFileName, data))
      case Failure(e: IOException) if e.getCause.isInstanceOf[StreamLimitReachedException] =>
        Future.failed(new IllegalArgumentException(s"Maximum (${config.maxSizeInBytes} bytes) attachment size exceeded."))
      case Failure(exception) => Future.failed(exception)
    }
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
