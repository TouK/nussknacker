package pl.touk.nussknacker.ui.db

import pl.touk.nussknacker.ui.db.entity._
import slick.jdbc.JdbcProfile

trait EspTables
  extends ProcessEntityFactory
    with CommentEntityFactory
    with ProcessVersionEntityFactory
    with EnvironmentsEntityFactory
    with ProcessDeploymentInfoEntityFactory
    with TagsEntityFactory
    with AttachmentEntityFactory {
  protected val profile: JdbcProfile

}
