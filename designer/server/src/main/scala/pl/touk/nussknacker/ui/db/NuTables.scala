package pl.touk.nussknacker.ui.db

import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory
import slick.jdbc.JdbcProfile

trait NuTables
    extends ProcessEntityFactory
    with ProcessVersionEntityFactory
    with EnvironmentsEntityFactory
    with ScenarioActivityEntityFactory
    with ScenarioLabelsEntityFactory
    with AttachmentEntityFactory
    with DeploymentEntityFactory
    with StickyNotesEntityFactory {
  protected val profile: JdbcProfile

}
