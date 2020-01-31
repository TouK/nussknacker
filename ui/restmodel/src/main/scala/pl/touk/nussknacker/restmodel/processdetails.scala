package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.JsonCodec
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessId => ApiProcessId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}

object processdetails extends JavaTimeEncoders with JavaTimeDecoders {
  sealed trait Process {
    val lastAction: Option[ProcessDeploymentAction]
    def isDeployed: Boolean = lastAction.exists(_.isDeployed)
    def isCanceled: Boolean = lastAction.exists(_.isCanceled)
  }

  object BasicProcess {
    def apply[ProcessShape](baseProcessDetails: BaseProcessDetails[ProcessShape]) = new BasicProcess(
      id = baseProcessDetails.id,
      name = ProcessName(baseProcessDetails.name),
      processId = baseProcessDetails.processId,
      processVersionId = baseProcessDetails.processVersionId,
      isSubprocess = baseProcessDetails.isSubprocess,
      isArchived = baseProcessDetails.isArchived,
      processCategory = baseProcessDetails.processCategory,
      processType = baseProcessDetails.processType,
      processingType = baseProcessDetails.processingType,
      modificationDate = baseProcessDetails.modificationDate,
      createdAt = baseProcessDetails.createdAt,
      createdBy = baseProcessDetails.createdBy,
      lastAction = baseProcessDetails.lastAction,
      lastDeployedAction = baseProcessDetails.lastDeployedAction,
      state = baseProcessDetails.state
    )
  }

  @JsonCodec case class BasicProcess(id: String,
                                     name: ProcessName,
                                     processId: ApiProcessId,
                                     processVersionId: Long,
                                     isArchived: Boolean,
                                     isSubprocess: Boolean,
                                     processCategory: String,
                                     processType: ProcessType,
                                     processingType: ProcessingType,
                                     modificationDate: LocalDateTime,
                                     createdAt: LocalDateTime,
                                     createdBy: String,
                                     lastAction: Option[ProcessDeploymentAction],
                                     lastDeployedAction: Option[ProcessDeploymentAction],
                                     state: Option[ProcessStatus] = Option.empty //It temporary holds mapped action -> status. Now this field is fill at router. In future we will keep there cached sate
                                     ) extends Process

  object BaseProcessDetails {
    //It's necessary to encode / decode ProcessState
    import ProcessState._
    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = deriveEncoder
    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = deriveDecoder
  }

  case class BaseProcessDetails[ProcessShape](id: String,
                                              name: String,
                                              processId: ApiProcessId,
                                              processVersionId: Long,
                                              isLatestVersion: Boolean,
                                              description: Option[String],
                                              isArchived: Boolean,
                                              isSubprocess: Boolean,
                                              processType: ProcessType,
                                              processingType: ProcessingType,
                                              processCategory: String,
                                              modificationDate: LocalDateTime,
                                              createdAt: LocalDateTime,
                                              createdBy: String,
                                              tags: List[String],
                                              lastDeployedAction: Option[ProcessDeploymentAction],
                                              lastAction: Option[ProcessDeploymentAction],
                                              json: Option[ProcessShape],
                                              history: List[ProcessHistoryEntry],
                                              modelVersion: Option[Int],
                                              state: Option[ProcessStatus] = Option.empty //It temporary holds mapped action -> status. Now this field is fill at router. In future we will keep there cached sate
                                             ) extends Process {
    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = json.map(action))
    // todo: unsafe toLong; we need it for now - we use this class for both backend (id == real id) and frontend (id == name) purposes
    def idWithName: ProcessIdWithName = ProcessIdWithName(ProcessId(processId.value), ProcessName(name))
  }

  // TODO we should split ProcessDetails and ProcessShape (json), than it won't be needed. Also BasicProcess won't be necessary than.
  sealed trait ProcessShapeFetchStrategy[ProcessShape]

  object ProcessShapeFetchStrategy {
    // TODO: Find places where FetchDisplayable is used and think about replacing it with FetchCanonical. We generally should use displayable representation
    //       only in GUI (not for deployment or exports) and in GUI it should be post processed using ProcessDictSubstitutor
    implicit case object FetchDisplayable extends ProcessShapeFetchStrategy[DisplayableProcess]
    implicit case object FetchCanonical extends ProcessShapeFetchStrategy[CanonicalProcess]
    // In fact Unit won't be returned inside shape and Nothing would be more verbose but it won't help in compilation because Nothing <: DisplayableProcess
    implicit case object NotFetch extends ProcessShapeFetchStrategy[Unit]
  }

  type ProcessDetails = BaseProcessDetails[DisplayableProcess]

  type ValidatedProcessDetails = BaseProcessDetails[ValidatedDisplayableProcess]

  @JsonCodec case class ProcessHistoryEntry(processId: String,
                                            processName: String,
                                            processVersionId: Long,
                                            createDate: LocalDateTime,
                                            user: String)

  @JsonCodec case class DeploymentHistoryEntry(processVersionId: Long,
                                               time: LocalDateTime,
                                               user: String,
                                               deploymentAction: ProcessActionType,
                                               commentId: Option[Long],
                                               comment: Option[String],
                                               buildInfo: Map[String, String]) {

    def isDeployed: Boolean = deploymentAction.equals(ProcessActionType.Deploy)
    def isCanceled: Boolean = deploymentAction.equals(ProcessActionType.Cancel)
  }

  @JsonCodec case class ProcessDeploymentAction(processVersionId: Long,
                                                @Deprecated environment: String, //TODO: remove it in future..
                                                deployedAt: LocalDateTime,
                                                user: String,
                                                action: ProcessActionType,
                                                buildInfo: Map[String, String]) {
    def isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
    def isCanceled: Boolean = action.equals(ProcessActionType.Cancel)
  }
}
