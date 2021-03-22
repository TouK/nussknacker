package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}

@JsonCodec case class ProcessVersion(versionId: Long,
                                     processName: ProcessName,
                                     processId: ProcessId,
                                     user: String,
                                     modelVersion: Option[Int])

object ProcessVersion {
  //only for testing etc.
  val empty: ProcessVersion = ProcessVersion(versionId = 1,
    processName = ProcessName(""),
    processId = ProcessId(1),
    user = "",
    modelVersion = None
  )
}

