package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.defaults.deployment.DefaultProcessStateConfigurator

trait ProcessStateConfigurator {
  def processStateStatus: Enumeration
  def statusTooltips: Map[StateStatus, String]
  def statusIcons: Map[StateStatus, String]
  def isRunning(statusState: String): Boolean
  def isDuringDeploy(statusState: String): Boolean
  def isFinished(statusState: String): Boolean
  def getStatusActions(status: StateStatus): List[StateAction]
}

object ProcessState {
  def apply(deploymentId: DeploymentId,
            status: StateStatus,
            version: Option[ProcessVersion] = Option.empty,
            allowedActions: List[StateAction] = List.empty,
            startTime: Option[Long] = Option.empty,
            attributes: Option[Json] = Option.empty,
            errorMessage: Option[String] = Option.empty): ProcessState =
    new ProcessState(
      deploymentId,
      status.toString,
      allowedActions,
      version,
      startTime,
      attributes,
      errorMessage
    )
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: String,
                                   allowedActions: List[StateAction],
                                   version: Option[ProcessVersion],
                                   startTime: Option[Long],
                                   attributes: Option[Json],
                                   errorMessage: Option[String])

object StateAction extends Enumeration {
  implicit val typeEncoder: Encoder[StateAction.Value] = Encoder.enumEncoder(StateAction)
  implicit val typeDecoder: Decoder[StateAction.Value] = Decoder.enumDecoder(StateAction)

  type StateAction = Value
  val Deploy: Value = Value("DEPLOY")
  val Cancel: Value = Value("CANCEL")
  val Pause: Value = Value("PAUSE") //TODO: To implement in future..
}

object StatusState extends Enumeration {
  class Value(name: String) extends Val(name)
  type StateStatus = Value

  def verify(status: String, excepted: StateStatus): Boolean = status.equals(excepted.toString())

  def verify(status: StateStatus, excepted: StateStatus): Boolean = status.equals(excepted)
}
