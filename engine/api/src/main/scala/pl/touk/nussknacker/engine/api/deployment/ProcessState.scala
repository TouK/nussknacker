package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction

trait ProcessStateConfigurator {
  def statusTooltips: Map[StateStatus, String]
  def statusIcons: Map[StateStatus, String]
  def isRunning(statusState: StateStatus): Boolean
  def isDuringDeploy(statusState: StateStatus): Boolean
  def isFinished(statusState: StateStatus): Boolean
  def getStatusActions(status: StateStatus): List[StateAction]
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: StateStatus,
                                   version: Option[ProcessVersion] = Option.empty,
                                   allowedActions: List[StateAction] = List.empty,
                                   startTime: Option[Long] = Option.empty,
                                   attributes: Option[Json] = Option.empty,
                                   errorMessage: Option[String] = Option.empty)

object StateAction extends Enumeration {
  implicit val typeEncoder: Encoder[StateAction.Value] = Encoder.enumEncoder(StateAction)
  implicit val typeDecoder: Decoder[StateAction.Value] = Decoder.enumDecoder(StateAction)

  type StateAction = Value
  val Deploy: Value = Value("DEPLOY")
  val Cancel: Value = Value("CANCEL")
  val Pause: Value = Value("PAUSE") //TODO: To implement in future..
}

@JsonCodec case class StateStatus(name: String)


