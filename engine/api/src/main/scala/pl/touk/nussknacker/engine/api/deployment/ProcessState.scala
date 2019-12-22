package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus

trait ProcessStatePresenter{
  def presentTooltipMessage(status: StateStatus): String
  def presentIcon(status: StateStatus): String
}

object ProcessState {
  def apply(deploymentId: DeploymentId,
            status: StateStatus,
            statePresenter: ProcessStatePresenter,
            version: Option[ProcessVersion] = Option.empty,
            allowedActions: List[StateAction] = List.empty,
            startTime: Option[Long] = Option.empty,
            attributes: Option[Json] = Option.empty,
            errorMessage: Option[String] = Option.empty): ProcessState = new ProcessState(
      deploymentId,
      status.toString,
      statePresenter.presentTooltipMessage(status),
      statePresenter.presentIcon(status),
      allowedActions,
      version,
      startTime,
      attributes,
      errorMessage
    )

  def custom(deploymentId: DeploymentId,
             status: StateStatus,
             statePresenter: ProcessStatePresenter = ProcessStateCustomPresenter,
             version: Option[ProcessVersion] = Option.empty,
             startTime: Option[Long] = Option.empty,
             attributes: Option[Json] = Option.empty,
             errorMessage: Option[String] = Option.empty) = ProcessState(
    deploymentId = deploymentId,
    status = status,
    statePresenter = statePresenter,
    version = version,
    allowedActions = ProcessStateCustoms.getStatusActions(status),
    startTime = startTime,
    attributes = attributes,
    errorMessage = errorMessage
  )
}

@JsonCodec case class ProcessState(deploymentId: DeploymentId,
                                   status: String,
                                   tooltip: String,
                                   icon: String,
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

object StateStatus extends Enumeration {
  class Value(name: String) extends Val(name)

  type StateStatus = Value
  val Unknown = new Value("UNKNOWN")
  val NotDeployed = new Value("NOT_DEPLOYED")
  val DuringDeploy = new Value("DURING_DEPLOY")
  val Running = new Value("RUNNING")
  val Restarting = new Value("RESTARTING")
  val Failed = new Value("FAILED")
  val DuringCancel = new Value("DURING_CANCEL")
  val Canceled = new Value("CANCELED")
  val Finished = new Value("FINISHED")

  def verify(status: String, excepted: StateStatus): Boolean = status.equals(excepted.toString())

  def isFinished(state: ProcessState): Boolean = verify(state.status, Finished)

  def isRunning(state: ProcessState): Boolean = verify(state.status, Running)
}
