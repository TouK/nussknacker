package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.entity.CommentEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUser, LoggedUser, RealLoggedUser}
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

class CommentRepository(protected val dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def saveComment(
      scenarioId: ProcessId,
      scenarioGraphVersionId: VersionId,
      user: LoggedUser,
      comment: Comment
  ): DB[CommentEntityData] = {
    for {
      newId <- nextId
      entityData = CommentEntityData(
        id = newId,
        processId = scenarioId,
        processVersionId = scenarioGraphVersionId,
        content = comment.value,
        user = user.username,
        impersonatedByIdentity = user.getImpersonatingUserId,
        impersonatedByUsername = user.getImpersonatingUserName,
        createDate = Timestamp.from(Instant.now())
      )
      _ <- commentsTable += entityData
    } yield entityData
  }

  private def nextId[T <: JdbcProfile]: DBIO[Long] = {
    Sequence[Long]("process_comments_id_sequence").next.result
  }

}
