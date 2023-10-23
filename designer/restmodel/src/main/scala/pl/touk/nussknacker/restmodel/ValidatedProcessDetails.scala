package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessId => ApiProcessId, VersionId}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, BasicProcess, ProcessVersion}

import java.time.Instant

// TODO: Rename, clean up
@JsonCodec
final case class ValidatedProcessDetails(
    id: String,
    name: String,
    processId: ApiProcessId,
    processVersionId: VersionId,
    isLatestVersion: Boolean,
    description: Option[String],
    isArchived: Boolean,
    isFragment: Boolean,
    processingType: ProcessingType,
    processCategory: String,
    modificationDate: Instant,
    modifiedAt: Instant,
    modifiedBy: String,
    createdAt: Instant,
    createdBy: String,
    tags: List[String],
    lastDeployedAction: Option[ProcessAction],
    lastStateAction: Option[ProcessAction],
    lastAction: Option[ProcessAction],
    json: ValidatedDisplayableProcess,
    history: List[ProcessVersion],
    modelVersion: Option[Int],
    state: Option[ProcessState]
) {

  // TODO: Do we need all these conversions?
  def toBasicProcess: BasicProcess = BasicProcess(toDisplayableProcessDetails)

  def toDisplayableProcessDetails: BaseProcessDetails[DisplayableProcess] = {
    BaseProcessDetails(
      id = id,
      name = name,
      processId = processId,
      processVersionId = processVersionId,
      isLatestVersion = isLatestVersion,
      description = description,
      isArchived = isArchived,
      isFragment = isFragment,
      processingType = processingType,
      processCategory = processCategory,
      modificationDate = modificationDate,
      modifiedAt = modifiedAt,
      modifiedBy = modifiedBy,
      createdAt = createdAt,
      createdBy = createdBy,
      tags = tags,
      lastDeployedAction = lastDeployedAction,
      lastStateAction = lastStateAction,
      lastAction = lastAction,
      json = json.toDisplayable,
      history = history,
      modelVersion = modelVersion,
      state = state
    )
  }

}

object ValidatedProcessDetails {

  def apply(details: BaseProcessDetails[ValidatedDisplayableProcess]): ValidatedProcessDetails = {
    ValidatedProcessDetails(
      id = details.id,
      name = details.name,
      processId = details.processId,
      processVersionId = details.processVersionId,
      isLatestVersion = details.isLatestVersion,
      description = details.description,
      isArchived = details.isArchived,
      isFragment = details.isFragment,
      processingType = details.processingType,
      processCategory = details.processCategory,
      modificationDate = details.modificationDate,
      modifiedAt = details.modifiedAt,
      modifiedBy = details.modifiedBy,
      createdAt = details.createdAt,
      createdBy = details.createdBy,
      tags = details.tags,
      lastDeployedAction = details.lastDeployedAction,
      lastStateAction = details.lastStateAction,
      lastAction = details.lastAction,
      json = details.json,
      history = details.history,
      modelVersion = details.modelVersion,
      state = details.state
    )
  }

}
