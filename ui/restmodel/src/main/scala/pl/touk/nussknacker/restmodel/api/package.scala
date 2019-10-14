package pl.touk.nussknacker.restmodel

package object api {
  case class AttachmentToAdd(processId: Long,
                             processVersionId: Long,
                             fileName: String,
                             relativeFilePath: String)
}
