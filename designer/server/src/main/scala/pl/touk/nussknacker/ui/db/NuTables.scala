package pl.touk.nussknacker.ui.db

import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory
import slick.jdbc.JdbcProfile

trait NuTables
    extends ProcessEntityFactory
    with CommentEntityFactory
    with ProcessVersionEntityFactory
    with EnvironmentsEntityFactory
    with ProcessActionEntityFactory
    with ScenarioLabelsEntityFactory
    with AttachmentEntityFactory
    with DeploymentEntityFactory {
  protected val profile: JdbcProfile

}
