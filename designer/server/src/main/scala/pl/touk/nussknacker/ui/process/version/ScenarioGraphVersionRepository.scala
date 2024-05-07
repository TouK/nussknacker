package pl.touk.nussknacker.ui.process.version

import db.util.DBIOActionInstances.{DB, toEffectAll}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import slick.jdbc.JdbcProfile

class ScenarioGraphVersionRepository(dbRef: DbRef) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getLatestScenarioGraphVersion(scenarioId: ProcessId): DB[ProcessVersionEntityData] = {
    toEffectAll(
      // We create an initial version when we create scenario so we are sure that the latest version will always exist
      processVersionsTableWithScenarioJson.filter(_.processId === scenarioId).take(1).result.head
    )
  }

}
