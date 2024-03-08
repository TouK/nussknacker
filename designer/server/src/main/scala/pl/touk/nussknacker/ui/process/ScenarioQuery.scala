package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.process.ProcessName

final case class ScenarioQuery(
    isFragment: Option[Boolean] = None,
    isArchived: Option[Boolean] = None,
    isDeployed: Option[Boolean] = None,
    categories: Option[Seq[String]] = None,
    processingTypes: Option[Seq[String]] = None,
    names: Option[Seq[ProcessName]] = None,
)

object ScenarioQuery {
  def empty: ScenarioQuery = ScenarioQuery(None, None, None, None, None, None)

  // TODO: rename to nonArchived
  def unarchived: ScenarioQuery = empty.copy(isArchived = Some(false))

  def unarchivedProcesses: ScenarioQuery = unarchived.copy(isFragment = Some(false))

  def unarchivedFragments: ScenarioQuery = unarchived.copy(isFragment = Some(true))

  def deployed: ScenarioQuery = unarchivedProcesses.copy(isDeployed = Some(true))

}
