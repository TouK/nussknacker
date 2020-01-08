package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import io.circe.syntax._

// We return for icons and tooltips maps because of optimization. Base configurations for tooltips and icons are configured at FR side.
// For each manager we return overrated configuration (or new configuration) because we don't want return for each state big string with svg.
trait ProcessStateDefinitionManager {
  def getStatusActions(stateStatus: StateStatus): List[StateAction]
  def statusTooltips: Map[StateStatus, String]
  def statusIcons: Map[StateStatus, String]
}

object ProcessState {
  implicit val typeEncoder: Encoder[StateStatus] = Encoder.encodeJson.contramap(st => StateStatusCodec(st.getClass.getName, st.name).asJson)
  implicit val typeDecoder: Decoder[StateStatus] = Decoder.decodeNone.map(_ => null) //TODO: Add decode implementation by clazz and value. At now we don't need it.

  @JsonCodec case class StateStatusCodec(clazz: String, value: String)

  def apply(deploymentId: DeploymentId,
            status: StateStatus,
            version: Option[ProcessVersion],
            allowedActions: List[StateAction] = List.empty,
            startTime: Option[Long] = Option.empty,
            attributes: Option[Json] = Option.empty,
            errorMessage: Option[String] = Option.empty): ProcessState =
    new ProcessState(deploymentId, status, version, allowedActions, startTime, attributes, errorMessage)


  def apply(deploymentId: String, status: StateStatus, version: Option[ProcessVersion], allowedActions: List[StateAction]): ProcessState =
    ProcessState(DeploymentId(deploymentId), status, version, allowedActions)
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: StateStatus,
                                   version: Option[ProcessVersion],
                                   allowedActions: List[StateAction],
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

object StateStatus {
  class StateStatus(val name: String) {
    def isOkForDeployed: Boolean = isDuringDeploy || isRunning
    def isDuringDeploy: Boolean = false
    def isFinished: Boolean = false
    def isRunning: Boolean = false
  }

  final class DuringDeployStateStatus(name: String) extends StateStatus(name) {
    override def isDuringDeploy: Boolean = true
  }

  final class FinishedStateStatus(name: String) extends StateStatus(name) {
    override def isFinished: Boolean = true
  }

  final class RunningStateStatus(name: String) extends StateStatus(name) {
    override def isRunning: Boolean = true
  }
}
