package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{ACursor, Decoder, Encoder, Json, JsonObject}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, Pause, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessId => ApiProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{ProcessVersion => EngineProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType

import java.time.Instant

object processdetails {

  val StateActionsTypes: Set[ProcessActionType] = Set(Cancel, Deploy, Pause)

  object BasicProcess {

    def apply[ProcessShape](baseProcessDetails: BaseProcessDetails[ProcessShape]) = new BasicProcess(
      id = baseProcessDetails.id,
      name = ProcessName(baseProcessDetails.name),
      processId = baseProcessDetails.processId,
      processVersionId = baseProcessDetails.processVersionId,
      isFragment = baseProcessDetails.isFragment,
      isArchived = baseProcessDetails.isArchived,
      processCategory = baseProcessDetails.processCategory,
      processingType = baseProcessDetails.processingType,
      modificationDate = baseProcessDetails.modificationDate,
      modifiedAt = baseProcessDetails.modifiedAt,
      modifiedBy = baseProcessDetails.modifiedBy,
      createdAt = baseProcessDetails.createdAt,
      createdBy = baseProcessDetails.createdBy,
      lastAction = baseProcessDetails.lastAction,
      lastStateAction = baseProcessDetails.lastStateAction,
      lastDeployedAction = baseProcessDetails.lastDeployedAction,
      state = baseProcessDetails.state,
      isLatestVersion = baseProcessDetails.isLatestVersion,
    )

  }

  @JsonCodec final case class BasicProcess(
      id: String,
      name: ProcessName,
      processId: ApiProcessId,
      processVersionId: VersionId,
      isArchived: Boolean,
      isFragment: Boolean,
      processCategory: String,
      processingType: ProcessingType,
      modificationDate: Instant,
      modifiedAt: Instant,
      modifiedBy: String,
      createdAt: Instant,
      createdBy: String,
      lastAction: Option[ProcessAction],
      lastStateAction: Option[ProcessAction],
      lastDeployedAction: Option[ProcessAction],
      // "State" is empty only for a while - just after fetching from DB, after that it is is filled by state computed based on DeploymentManager state.
      // After that it remains always defined.
      state: Option[ProcessState] = Option.empty,
      isLatestVersion: Boolean,
  )

  object BaseProcessDetails {
    // It's necessary to encode / decode ProcessState
    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = deriveConfiguredEncoder

    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = deriveConfiguredDecoder
  }

  final case class BaseProcessDetails[ProcessShape](
      id: String, // It temporary holds the name of process, because it's used everywhere in GUI - TODO: change type to ProcessId and explicitly use processName
      name: String,
      processId: ApiProcessId, // TODO: Remove it when we will support Long / ProcessId
      processVersionId: VersionId,
      isLatestVersion: Boolean,
      description: Option[String],
      isArchived: Boolean,
      isFragment: Boolean,
      processingType: ProcessingType,
      processCategory: String,
      modificationDate: Instant, // TODO: Deprecated, please use modifiedAt
      modifiedAt: Instant,
      modifiedBy: String,
      createdAt: Instant,
      createdBy: String,
      tags: Option[List[String]],
      lastDeployedAction: Option[ProcessAction],
      lastStateAction: Option[
        ProcessAction
      ], // State action is an action that can have an influence on the presented state of the scenario. We currently use it to distinguish between cancel / not_deployed and to detect inconsistent states between the designer and engine
      lastAction: Option[
        ProcessAction
      ], // TODO: Consider replacing it by lastStateAction, check were on FE we use lastAction, eg. archive date at the archive list
      json: ProcessShape,
      history: Option[List[ProcessVersion]],
      modelVersion: Option[Int],
      // "State" is empty only for a while - just after fetching from DB, after that it is is filled by state computed based on DeploymentManager state.
      // After that it remains always defined.
      state: Option[ProcessState] = Option.empty
  ) {
    lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, ProcessName(name))

    def mapProcess[NewShape](action: ProcessShape => NewShape): BaseProcessDetails[NewShape] = copy(json = action(json))

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

    implicit case object FetchComponentsUsages extends ProcessShapeFetchStrategy[ScenarioComponentsUsages]
  }

  type ProcessDetails = BaseProcessDetails[DisplayableProcess]

  type ValidatedProcessDetails = BaseProcessDetails[ValidatedDisplayableProcess]

  @JsonCodec final case class ProcessVersion(
      processVersionId: VersionId,
      createDate: Instant,
      user: String,
      modelVersion: Option[Int],
      actions: List[ProcessAction]
  )

}
