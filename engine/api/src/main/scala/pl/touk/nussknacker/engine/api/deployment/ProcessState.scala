package pl.touk.nussknacker.engine.api.deployment

import java.net.URI
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction

trait ProcessStateDefinitionManager {
  def statusActions(stateStatus: StateStatus): List[StateAction]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
}

object ProcessState {
  import io.circe.syntax._

  implicit val statusEncoder: Encoder[StateStatus] = Encoder.encodeJson.contramap(st => StateStatusCodec(st.getClass.getName, st.name).asJson)
  implicit val statusDecoder: Decoder[StateStatus] = Decoder.decodeNone.map(_ => null) //TODO: Add decode implementation by clazz and value. At now we don't need it.
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)

  def apply(deploymentId: String, status: StateStatus, version: Option[ProcessVersion], allowedActions: List[StateAction]): ProcessState =
    ProcessState(DeploymentId(deploymentId), status, version, allowedActions)

  @JsonCodec case class StateStatusCodec(clazz: String, value: String)
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: StateStatus,
                                   version: Option[ProcessVersion] = Option.empty,
                                   allowedActions: List[StateAction] = List.empty,
                                   icon: Option[URI] = Option.empty,
                                   tooltip: Option[String] = Option.empty,
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

sealed trait StateStatus {
  def isDuringDeploy: Boolean = false
  def isFinished: Boolean = false
  def isRunning: Boolean = false
  def canDeploy: Boolean = false
  def name: String
}

final class NotEstablishedStateStatus(val name: String) extends StateStatus

final class StoppedStateStatus(val name: String) extends StateStatus {
  override def canDeploy: Boolean = true
}

final class DuringDeployStateStatus(val name: String) extends StateStatus {
  override def isDuringDeploy: Boolean = true
}

final class FinishedStateStatus(val name: String) extends StateStatus {
  override def isFinished: Boolean = true
  override def canDeploy: Boolean = true
}

final class RunningStateStatus(val name: String) extends StateStatus {
  override def isRunning: Boolean = true
}