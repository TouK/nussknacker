package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId, ProcessId => ApiProcessId}
import pl.touk.nussknacker.engine.api.{ProcessVersion => EngineProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}

import java.time.Instant

object processdetails {

  sealed trait Process {
    val lastAction: Option[ProcessAction]
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
      processingType = baseProcessDetails.processingType,
      modificationDate = baseProcessDetails.modificationDate,
      modifiedAt = baseProcessDetails.modifiedAt,
      modifiedBy = baseProcessDetails.modifiedBy,
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
                                     processVersionId: VersionId,
                                     isArchived: Boolean,
                                     isSubprocess: Boolean,
                                     processCategory: String,
                                     processingType: ProcessingType,
                                     modificationDate: Instant,
                                     modifiedAt: Instant,
                                     modifiedBy: String,
                                     createdAt: Instant,
                                     createdBy: String,
                                     lastAction: Option[ProcessAction],
                                     lastDeployedAction: Option[ProcessAction],
                                     // "State" is empty only for a while - just after fetching from DB, after that it is is filled by state computed based on DeploymentManager state.
                                     // After that it remains always defined.
                                     state: Option[ProcessState] = Option.empty
                                    ) extends Process

  object BaseProcessDetails {
    //It's necessary to encode / decode ProcessState
    import ProcessState._
    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = deriveConfiguredEncoder
    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = deriveConfiguredDecoder
  }

  case class BaseProcessDetails[ProcessShape](id: String, //It temporary holds the name of process, because it's used everywhere in GUI - TODO: change type to ProcessId and explicitly use processName
                                              name: String,
                                              processId: ApiProcessId, //TODO: Remove it when we will support Long / ProcessId
                                              processVersionId: VersionId,
                                              isLatestVersion: Boolean,
                                              description: Option[String],
                                              isArchived: Boolean,
                                              isSubprocess: Boolean,
                                              processingType: ProcessingType,
                                              processCategory: String,
                                              modificationDate: Instant, //TODO: Deprecated, please use modifiedAt
                                              modifiedAt: Instant,
                                              modifiedBy: String,
                                              createdAt: Instant,
                                              createdBy: String,
                                              tags: List[String],
                                              lastDeployedAction: Option[ProcessAction],
                                              lastAction: Option[ProcessAction],
                                              json: ProcessShape,
                                              history: List[ProcessVersion],
                                              modelVersion: Option[Int],
                                              // "State" is empty only for a while - just after fetching from DB, after that it is is filled by state computed based on DeploymentManager state.
                                              // After that it remains always defined.
                                              state: Option[ProcessState] = Option.empty
                                             ) extends Process {
    lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, ProcessName(name))

    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = action(json))

    def toEngineProcessVersion: EngineProcessVersion = EngineProcessVersion(
      versionId = processVersionId,
      processName = idWithName.name,
      processId = processId,
      user = modifiedBy,
      modelVersion = modelVersion
    )
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

  @JsonCodec case class ProcessVersion(processVersionId: VersionId,
                                       createDate: Instant,
                                       user: String,
                                       modelVersion: Option[Int],
                                       actions: List[ProcessAction])


}
