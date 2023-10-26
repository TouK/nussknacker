package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.process.ProcessName

final case class ProcessesQuery(
    isFragment: Option[Boolean] = None,
    isArchived: Option[Boolean] = None,
    isDeployed: Option[Boolean] = None,
    categories: Option[Seq[String]] = None,
    processingTypes: Option[Seq[String]] = None,
    names: Option[Seq[ProcessName]] = None,
)

object ProcessesQuery {
  def empty: ProcessesQuery = ProcessesQuery(None, None, None, None, None, None)

  def unarchived: ProcessesQuery = empty.copy(isArchived = Some(false))

  def unarchivedProcesses: ProcessesQuery = unarchived.copy(isFragment = Some(false))

  def unarchivedFragments: ProcessesQuery = unarchived.copy(isFragment = Some(true))

  def deployed: ProcessesQuery = unarchivedProcesses.copy(isDeployed = Some(true))

}
