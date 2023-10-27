package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{ProcessVersion => EngineProcessVersion}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails

import java.time.Instant

// TODO we should split ScenarioDetails and ScenarioShape (json)
// This class is in the listener module, because it was necessary for PullProcessRepository
// but it is kind of internal, intermediate representation of scenario details that is returned by designer's
// internal FetchingProcessRepository
final case class RepositoryScenarioWithDetails[ScenarioShape](
    id: String, // It temporary holds the name of process, because it's used everywhere in GUI - TODO: change type to ProcessId and explicitly use processName
    name: ProcessName,
    processId: ProcessId, // TODO: Remove it when we will support Long / ProcessId
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
    json: ScenarioShape,
    history: Option[List[scenariodetails.ScenarioVersion]],
    modelVersion: Option[Int]
) {
  lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, name)

  def mapScenario[NewShape](action: ScenarioShape => NewShape): RepositoryScenarioWithDetails[NewShape] =
    copy(json = action(json))

  def toEngineProcessVersion: EngineProcessVersion = EngineProcessVersion(
    versionId = processVersionId,
    processName = idWithName.name,
    processId = processId,
    user = modifiedBy,
    modelVersion = modelVersion
  )

}
