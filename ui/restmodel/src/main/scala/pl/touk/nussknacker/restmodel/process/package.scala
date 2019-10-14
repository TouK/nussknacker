package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import io.circe.generic.JsonCodec
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.db.entity.{AttachmentEntityData, CommentEntityData}

package object process extends JavaTimeDecoders with JavaTimeEncoders {
  final case class ProcessId(value: Long) extends AnyVal

  final case class ProcessIdWithName(id: ProcessId, name: ProcessName)

  @JsonCodec case class ProcessActivity(comments: List[Comment], attachments: List[Attachment])

  @JsonCodec case class Attachment(id: Long, processId: String, processVersionId: Long, fileName: String, user: String, createDate: LocalDateTime)

  object Attachment {
    def apply(attachment: AttachmentEntityData, processName: String): Attachment = {
      Attachment(
        id = attachment.id,
        processId = processName,
        processVersionId = attachment.processVersionId,
        fileName = attachment.fileName,
        user = attachment.user,
        createDate = attachment.createDateTime
      )
    }
  }

  @JsonCodec case class Comment(id: Long, processId: String, processVersionId: Long, content: String, user: String, createDate: LocalDateTime)
  object Comment {
    def apply(comment: CommentEntityData, processName: String): Comment = {
      Comment(
        id = comment.id,
        processId = processName,
        processVersionId = comment.processVersionId,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDateTime
      )
    }
  }
}
