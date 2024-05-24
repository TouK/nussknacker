package pl.touk.nussknacker.ui.db.entity

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUser, LoggedUser, RealLoggedUser}
import slick.jdbc.JdbcProfile
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull
import pl.touk.nussknacker.ui.listener.Comment

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

trait CommentEntityFactory extends BaseEntityFactory {

  import profile.api._

  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity] = LTableQuery(new CommentEntity(_))

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def id: Rep[Long] = column[Long]("id", O.PrimaryKey)

    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def content: Rep[String] = column[String]("content", NotNull)

    def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    def impersonatedBy: Rep[Option[String]] = column[Option[String]]("impersonated_by")

    override def * = (id, processId, processVersionId, content, user, impersonatedBy, createDate) <> (
      CommentEntityData.apply _ tupled, CommentEntityData.unapply
    )

  }

}

final case class CommentEntityData(
    id: Long,
    processId: ProcessId,
    processVersionId: VersionId,
    content: String,
    user: String,
    impersonatedBy: Option[String],
    createDate: Timestamp
) {
  val createDateTime: Instant = createDate.toInstant
}

trait CommentActions {
  protected val profile: JdbcProfile
  import profile.api._

  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]

  def nextIdAction[T <: JdbcProfile](implicit jdbcProfile: T): DBIO[Long] = {
    Sequence[Long]("process_comments_id_sequence").next.result
  }

  def newCommentAction(processId: ProcessId, processVersionId: => VersionId, comment: Option[Comment])(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): DB[Option[CommentEntityData]] = {
    comment match {
      case Some(c) if c.value.nonEmpty =>
        for {
          newId <- nextIdAction
          entityData = CommentEntityData(
            id = newId,
            processId = processId,
            processVersionId = processVersionId,
            content = c.value,
            user = loggedUser.username,
            impersonatedBy = loggedUser match {
              case _: RealLoggedUser   => None
              case u: ImpersonatedUser => Some(u.impersonatingUser.id)
            },
            createDate = Timestamp.from(Instant.now())
          )
          _ <- commentsTable += entityData
        } yield Some(entityData)
      case _ => DBIO.successful(None)
    }
  }

}
