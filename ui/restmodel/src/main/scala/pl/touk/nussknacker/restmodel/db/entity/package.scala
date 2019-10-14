package pl.touk.nussknacker.restmodel.db

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.nussknacker.restmodel.util.DateUtils

package object entity {
  case class AttachmentEntityData(id: Long, processId: Long, processVersionId: Long, fileName: String, filePath: String, user: String, createDate: Timestamp) {
    val createDateTime: LocalDateTime = DateUtils.toLocalDateTime(createDate)
  }

  case class CommentEntityData(id: Long, processId: Long, processVersionId: Long, content: String, user: String, createDate: Timestamp) {
    val createDateTime: LocalDateTime = DateUtils.toLocalDateTime(createDate)
  }
}
