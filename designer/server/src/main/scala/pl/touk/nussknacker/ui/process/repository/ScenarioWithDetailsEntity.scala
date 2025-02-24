package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{
  ProcessId,
  ProcessIdWithName,
  ProcessName,
  ProcessingType,
  ScenarioVersion,
  VersionId
}
import pl.touk.nussknacker.engine.api.{ProcessVersion => EngineProcessVersion}
import pl.touk.nussknacker.ui.listener.ListenerScenarioWithDetails

import java.time.Instant

// TODO we should split ScenarioDetails and ScenarioShape (json)
final case class ScenarioWithDetailsEntity[ScenarioShape](
    name: ProcessName,
    processId: ProcessId,
    processVersionId: VersionId,
    isLatestVersion: Boolean,
    description: Option[String],
    isArchived: Boolean,
    isFragment: Boolean,
    processingType: ProcessingType,
    // FIXME: remove
    processCategory: String,
    modificationDate: Instant, // TODO: Deprecated, please use modifiedAt
    modifiedAt: Instant,
    modifiedBy: String,
    createdAt: Instant,
    createdBy: String,
    scenarioLabels: List[String],
    lastDeployedAction: Option[ProcessAction],
    lastStateAction: Option[
      ProcessAction
    ], // State action is an action that can have an influence on the presented state of the scenario. We currently use it to distinguish between cancel / not_deployed and to detect inconsistent states between the designer and engine
    lastAction: Option[
      ProcessAction
    ], // TODO: Consider replacing it by lastStateAction, check were on FE we use lastAction, eg. archive date at the archive list
    // TODO: Rename into scenarioGraph when we store DisplayableProcess instead of CanonicalProcess in the db
    json: ScenarioShape,
    history: Option[List[ScenarioVersion]],
    modelVersion: Option[Int]
) extends ListenerScenarioWithDetails {
  lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, name)

  def mapScenario[NewShape](action: ScenarioShape => NewShape): ScenarioWithDetailsEntity[NewShape] =
    copy(json = action(json))

  def toEngineProcessVersion: EngineProcessVersion = EngineProcessVersion(
    versionId = processVersionId,
    processName = name,
    processId = processId,
    labels = scenarioLabels,
    user = modifiedBy,
    modelVersion = modelVersion
  )

  override def scenarioGraph: ScenarioGraph = json match {
    case scenarioGraph: ScenarioGraph => scenarioGraph
    case other =>
      throw new IllegalStateException(
        s"ScenarioWithDetailsEntity doesn't hold DisplayableProcess, instead of this it holds: $other"
      )
  }

}
