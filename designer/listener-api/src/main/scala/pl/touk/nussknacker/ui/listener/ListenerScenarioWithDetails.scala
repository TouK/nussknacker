package pl.touk.nussknacker.ui.listener

import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, ScenarioVersion, VersionId}

import java.time.Instant

trait ListenerScenarioWithDetails {
  def name: ProcessName

  def processId: ProcessId

  def processVersionId: VersionId

  def isLatestVersion: Boolean

  def description: Option[String]

  def isArchived: Boolean

  def isFragment: Boolean

  def processingType: ProcessingType

  def processCategory: String

  def modificationDate: Instant // TODO: Deprecated, please use modifiedAt

  def modifiedAt: Instant

  def modifiedBy: String

  def createdAt: Instant

  def createdBy: String

  def scenarioLabels: List[String]

  def lastDeployedAction: Option[ProcessAction]

  def lastStateAction: Option[ProcessAction]

  def scenarioGraph: ScenarioGraph

  def history: Option[List[ScenarioVersion]]

  def modelVersion: Option[Int]
}
