package pl.touk.nussknacker.engine.api

case class ProcessVersion(versionId: Long,
                          processId: String,
                          user: String,
                          modelVersion: Option[Int]
                         )

object ProcessVersion {
  val empty = ProcessVersion(versionId = 1,
    processId = "",
    user = "",
    modelVersion = None)

}