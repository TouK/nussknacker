package pl.touk.nussknacker.ui.process.version

import db.util.DBIOActionInstances.{toEffectAll, DB}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class ScenarioGraphVersionRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getLatestScenarioGraphVersion(scenarioId: ProcessId): DB[ProcessVersionEntityData] = {
    toEffectAll(
      processVersionsTableWithScenarioJson
        .filter(_.processId === scenarioId)
        .sortBy(_.id.desc)
        .take(1)
        .result
        .headOption
        // We create an initial version when we create scenario so we are sure that the latest version will always exist
        .map(_.getOrElse(throw new IllegalStateException(s"Scenario [$scenarioId] without any graph version")))
    )
  }

}
