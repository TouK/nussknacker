package pl.touk.nussknacker.ui.process

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BoundedInputStream
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.config.AttachmentsConfig
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.{AttachmentDataWithName, AttachmentToAdd}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.io.InputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class ScenarioAttachmentService(config: AttachmentsConfig, scenarioActivityRepository: ProcessActivityRepository)(
    implicit ec: ExecutionContext
) extends LazyLogging {

  def saveAttachment(
      scenarioId: ProcessId,
      scenarioVersionId: VersionId,
      originalFileName: String,
      inputStream: InputStream
  )(implicit loggedUser: LoggedUser): Future[Unit] = {
    Future
      .apply(new BoundedInputStream(inputStream, config.maxSizeInBytes + 1))
      .map(Using.resource(_) { isResource => IOUtils.toByteArray(isResource) })
      .flatMap(bytes => {
        if (bytes.length > config.maxSizeInBytes) {
          Future.failed(
            new IllegalArgumentException(s"Maximum (${config.maxSizeInBytes} bytes) attachment size exceeded.")
          )
        } else {
          scenarioActivityRepository.addAttachment(
            AttachmentToAdd(scenarioId, scenarioVersionId, originalFileName, bytes)
          )
        }
      })
  }

  def readAttachment(attachmentId: Long): Future[Option[AttachmentDataWithName]] = {
    scenarioActivityRepository
      .findAttachment(attachmentId)
      .map(_.map(attachment => (attachment.fileName, attachment.data)))
  }

}

object ScenarioAttachmentService {
  type AttachmentDataWithName = (String, Array[Byte])

  final case class AttachmentToAdd(
      scenarioId: ProcessId,
      scenarioVersionId: VersionId,
      fileName: String,
      data: Array[Byte]
  )

}
