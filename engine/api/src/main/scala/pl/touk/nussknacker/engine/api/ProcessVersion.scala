package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.process.ProcessName

case class ProcessVersion(versionId: Long,
                          processName: ProcessName,
                          user: String,
                          modelVersion: Option[Int])

object ProcessVersion {
  val empty = ProcessVersion(versionId = 1,
    processName = ProcessName(""),
    user = "",
    modelVersion = None)
}