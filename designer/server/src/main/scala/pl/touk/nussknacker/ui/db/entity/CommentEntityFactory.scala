package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.time.Instant

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

    def impersonatedByIdentity = column[Option[String]]("impersonated_by_identity")

    // TODO impersonating user's name is added so it's easier to present the name on the fronted.
    // Once we have a mechanism for fetching username by user's identity impersonated_by_username column could be deleted from database tables.
    def impersonatedByUsername = column[Option[String]]("impersonated_by_username")

    override def * =
      (id, processId, processVersionId, content, user, impersonatedByIdentity, impersonatedByUsername, createDate) <> (
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
    impersonatedByIdentity: Option[String],
    impersonatedByUsername: Option[String],
    createDate: Timestamp
) {
  val createDateTime: Instant = createDate.toInstant
}
