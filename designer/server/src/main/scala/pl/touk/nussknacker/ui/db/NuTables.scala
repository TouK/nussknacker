package pl.touk.nussknacker.ui.db

import pl.touk.nussknacker.ui.customhttpservice.services.NuJdbcProfile
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory

trait NuTables
    extends ProcessEntityFactory
    with ProcessVersionEntityFactory
    with EnvironmentsEntityFactory
    with ScenarioActivityEntityFactory
    with ScenarioLabelsEntityFactory
    with AttachmentEntityFactory
    with DeploymentEntityFactory {

  protected val profile: NuJdbcProfile
}
