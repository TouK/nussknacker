package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

// This class holds all necessary for scenario running, scenario metadata. It is passed next to the scenario graph
// into DeploymentManager during deploy
// TODO: It should be called ScenarioRuntimeMetadata (See TODO in MetaData)
@JsonCodec case class ProcessVersion(
    versionId: VersionId,
    processName: ProcessName,
    processId: ProcessId,
    user: String,
    modelVersion: Option[Int]
)

object ProcessVersion {

  // only for testing etc.
  val empty: ProcessVersion = ProcessVersion(
    versionId = VersionId.initialVersionId,
    processName = ProcessName(""),
    processId = ProcessId(1),
    user = "",
    modelVersion = None
  )

}
