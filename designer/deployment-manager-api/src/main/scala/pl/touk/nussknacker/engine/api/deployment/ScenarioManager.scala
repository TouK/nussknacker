package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import slick.lifted.{Query, Rep}

trait ScenarioManager {

  def fetchCanonicalProcessesQuery: Query[
    (Rep[ProcessId], Rep[ProcessName], Rep[VersionId], Rep[String]),
    (ProcessId, ProcessName, VersionId, String),
    Seq
  ]

}

object NoOpScenarioManager extends ScenarioManager {

  def fetchCanonicalProcessesQuery: Query[
    (Rep[ProcessId], Rep[ProcessName], Rep[VersionId], Rep[String]),
    (ProcessId, ProcessName, VersionId, String),
    Seq
  ] = Query.apply[
    (Rep[ProcessId], Rep[ProcessName], Rep[VersionId], Rep[String]),
    (ProcessId, ProcessName, VersionId, String),
    (Rep[ProcessId], Rep[ProcessName], Rep[VersionId], Rep[String])
  ](Seq.empty)

}
