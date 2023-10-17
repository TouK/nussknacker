package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

// TODO: split it, rename it, currently it is meaningless bunch of data, sometimes some fields like user is duplicated
//       in DeploymentData which is passed next to it
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
