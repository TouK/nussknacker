package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName

@JsonCodec case class ProcessVersion(versionId: Long,
                                     processName: ProcessName,
                                     user: String,
                                     modelVersion: Option[Int])

object ProcessVersion {
  val empty = ProcessVersion(versionId = 1,
    processName = ProcessName(""),
    user = "",
    modelVersion = None
  )
}