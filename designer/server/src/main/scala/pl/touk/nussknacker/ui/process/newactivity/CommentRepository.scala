package pl.touk.nussknacker.ui.process.newactivity

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.entity.{CommentActions, CommentEntityData}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

class CommentRepository(protected val dbRef: DbRef)(implicit ec: ExecutionContext)
    extends CommentActions
    with NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def saveComment(
      scenarioId: ProcessId,
      scenarioGraphVersionId: VersionId,
      user: LoggedUser,
      comment: Comment
  ): DB[Unit] = {
    val entityData = CommentEntityData(
      id = -1,
      processId = scenarioId,
      processVersionId = scenarioGraphVersionId,
      content = comment.value,
      // TODO: User id instead
      user = user.username,
      createDate = Timestamp.from(Instant.now())
    )
    (commentsTable += entityData).map(_ => ())
  }

}
