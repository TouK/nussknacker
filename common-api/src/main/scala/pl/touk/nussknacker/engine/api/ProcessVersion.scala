package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

// We should split this class - see TODO in ScenarioAction
@JsonCodec case class ProcessVersion(
    versionId: VersionId,
    processName: ProcessName,
    processId: ProcessId,
    labels: List[String],
    user: String,
    modelVersion: Option[Int]
)

object ProcessVersion {

  // only for testing etc.
  val empty: ProcessVersion = ProcessVersion(
    versionId = VersionId.initialVersionId,
    processName = ProcessName(""),
    processId = ProcessId(1),
    labels = List.empty,
    user = "",
    modelVersion = None
  )

}
