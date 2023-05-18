package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant

@JsonCodec case class ProcessAction(processVersionId: VersionId,
                                    performedAt: Instant,
                                    user: String,
                                    action: ProcessActionType,
                                    commentId: Option[Long],
                                    comment: Option[String],
                                    buildInfo: Map[String, String]) {
  def isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
  def isCanceled: Boolean = action.equals(ProcessActionType.Cancel)
}
