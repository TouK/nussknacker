package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId, ProcessId => ApiProcessId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.engine.api.CirceUtil._

object processdetails {
  sealed trait Process {
    val lastAction: Option[ProcessAction]
    def isDeployed: Boolean = !isNotDeployed && lastAction.exists(_.isDeployed)
    def isCanceled: Boolean = !isNotDeployed && lastAction.exists(_.isCanceled)
    def isNotDeployed: Boolean = lastAction.isEmpty
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
                                     lastAction: Option[ProcessAction],
                                     lastDeployedAction: Option[ProcessAction],
                                     state: Option[ProcessState] = Option.empty //It temporary holds mapped action -> status. Now this field is fill at router. In future we will keep there cached sate
                                    ) extends Process

  object BaseProcessDetails {
    //It's necessary to encode / decode ProcessState
    import ProcessState._
    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = io.circe.derivation.deriveEncoder
    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = io.circe.derivation.deriveDecoder
  }

  case class BaseProcessDetails[ProcessShape](id: String, //It temporary holds the name of process, because it's used everywhere in GUI - TODO: change type to ProcessId and explicitly use processName
                                              name: String,
                                              processId: ApiProcessId, //TODO: Remove it when we will support Long / ProcessId
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
                                              lastDeployedAction: Option[ProcessAction],
                                              lastAction: Option[ProcessAction],
                                              json: Option[ProcessShape],
                                              history: List[ProcessVersion],
                                              modelVersion: Option[Int],
                                              state: Option[ProcessState] = Option.empty //It temporary holds mapped action -> status. Now this field is fill at router. In future we will keep there cached sate
                                             ) extends Process {
    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = json.map(action))

    lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, ProcessName(name))
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

  @JsonCodec case class ProcessVersion(//processId: Long, //TODO: support it when will support processId as Long / ProcessId
                                       processVersionId: Long,
                                       createDate: LocalDateTime,
                                       user: String,
                                       modelVersion: Option[Int],
                                       actions: List[ProcessAction])

  @JsonCodec case class ProcessAction( //processId: Long, //TODO: support it when will support processId as Long / ProcessId
                                       processVersionId: VersionId,
                                       performedAt: LocalDateTime,
                                       user: String,
                                       action: ProcessActionType,
                                       commentId: Option[Long],
                                       comment: Option[String],
                                       buildInfo: Map[String, String]) {
    def isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
    def isCanceled: Boolean = action.equals(ProcessActionType.Cancel)
  }
}
