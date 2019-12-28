package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.customs.deployment.ProcessStateCustomConfigurator

trait ProcessStateConfigurator {
  def getStatusActions(status: StateStatus): List[StateAction]
  def statusMessages: Option[Map[StateStatus, String]]
  def statusIcons: Option[Map[StateStatus, String]]
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

  def custom(deploymentId: DeploymentId,
             status: StateStatus,
             version: Option[ProcessVersion] = Option.empty,
             startTime: Option[Long] = Option.empty,
             attributes: Option[Json] = Option.empty,
             errorMessage: Option[String] = Option.empty): ProcessState =
    ProcessState(
      deploymentId = deploymentId,
      status = status,
      version = version,
      allowedActions = ProcessStateCustomConfigurator.getStatusActions(status),
      startTime = startTime,
      attributes = attributes,
      errorMessage = errorMessage
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

  val Running = new StateStatus("RUNNING")
  val Finished = new StateStatus("FINISHED")
  val DuringDeploy = new StateStatus("DURING_DEPLOY")

  def verify(status: String, excepted: StateStatus): Boolean = status.equals(excepted.toString())
  def verify(status: StateStatus, excepted: StateStatus): Boolean = status.equals(excepted)

  def isFinished(state: ProcessState): Boolean = verify(state.status, Finished)

  def isRunning(state: ProcessState): Boolean = verify(state.status, Running)

  def isDuringDeploy(state: ProcessState): Boolean = verify(state.status, DuringDeploy)

  def isDuringDeploy(status: String): Boolean = verify(status,DuringDeploy)

  def isRunning(status: String): Boolean = verify(status, Running)
}

// Each own StateStatus enum implementation should extends from StateStatusEnumeration because it has to implement
// base three statuses: Running / Finished / DuringDeploy
trait StateStatusEnumeration extends Enumeration {
  val Running = StatusState.Running
  val Finished = StatusState.Finished
  val DuringDeploy = StatusState.DuringDeploy
}