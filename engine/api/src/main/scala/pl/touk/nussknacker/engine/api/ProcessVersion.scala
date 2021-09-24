package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

@JsonCodec case class ProcessVersion(versionId: VersionId,
                                     processName: ProcessName,
                                     processId: ProcessId,
                                     user: String,
                                     modelVersion: Option[Int])

object ProcessVersion {
  //only for testing etc.
  val empty: ProcessVersion = ProcessVersion(versionId = VersionId(1),
    processName = ProcessName(""),
    processId = ProcessId(1),
    user = "",
    modelVersion = None
  )
}

