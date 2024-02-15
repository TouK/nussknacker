package pl.touk.nussknacker.test.utils.domain

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, NodeData}
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

import java.time.Instant
import java.util.UUID
import scala.util.Random

object TestProcessUtil {

  private val randomGenerator = new Random()

  def toCanonical(scenarioGraph: ScenarioGraph, processName: ProcessName): CanonicalProcess =
    CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processName)

  def toJson(espProcess: CanonicalProcess): Json =
    Encoder[ScenarioGraph].apply(CanonicalProcessConverter.toScenarioGraph(espProcess))

  def createScenarioEntity(
      name: String,
      category: String,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      lastAction: Option[ProcessActionType] = None,
      json: Option[ScenarioGraph] = None
  ): ScenarioWithDetailsEntity[ScenarioGraph] =
    wrapWithScenarioDetailsEntity(
      ProcessName(name),
      scenarioGraph = json,
      category,
      isFragment = false,
      isArchived,
      processingType,
      lastAction = lastAction
    )

  def createFragmentEntity(
      name: String,
      category: String,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      json: Option[ScenarioGraph] = None,
      lastAction: Option[ProcessActionType] = None
  ): ScenarioWithDetailsEntity[ScenarioGraph] = {
    val processName = ProcessName(name)
    wrapWithScenarioDetailsEntity(
      name = processName,
      category = category,
      isFragment = true,
      isArchived = isArchived,
      processingType = processingType,
      lastAction = lastAction,
      scenarioGraph = Some(json.getOrElse(sampleFragmentGraph))
    )
  }

  def wrapGraphWithScenarioDetailsEntity(
      name: ProcessName,
      scenarioGraph: ScenarioGraph,
      processingType: ProcessingType = TestProcessingTypes.Streaming,
      category: String = TestCategories.Category1,
      isArchived: Boolean = false,
      isFragment: Boolean = false
  ): ScenarioWithDetailsEntity[ScenarioGraph] =
    wrapWithScenarioDetailsEntity(
      name,
      scenarioGraph = Some(scenarioGraph),
      processingType = processingType,
      category = category,
      isArchived = isArchived,
      isFragment = isFragment
    )

  def wrapWithScenarioDetailsEntity(
      name: ProcessName,
      scenarioGraph: Option[ScenarioGraph] = None,
      category: String = TestCategories.Category1,
      isFragment: Boolean = false,
      isArchived: Boolean = false,
      processingType: ProcessingType = Streaming,
      lastAction: Option[ProcessActionType] = None,
      description: Option[String] = None,
      history: Option[List[ScenarioVersion]] = None
  ): ScenarioWithDetailsEntity[ScenarioGraph] = {
    val jsonData = scenarioGraph
      .getOrElse(createEmptyJson(processingType))
    repository.ScenarioWithDetailsEntity[ScenarioGraph](
      name = name,
      processId = ProcessId(generateId()),
      processVersionId = VersionId.initialVersionId,
      isLatestVersion = true,
      description = description,
      isArchived = isArchived,
      isFragment = isFragment,
      processingType = processingType,
      processCategory = category,
      modificationDate = Instant.now(),
      modifiedAt = Instant.now(),
      modifiedBy = "user1",
      createdAt = Instant.now(),
      createdBy = "user1",
      tags = None,
      lastAction = lastAction.map(createProcessAction),
      lastStateAction = lastAction.collect {
        case action if ProcessActionType.StateActionsTypes.contains(action) => createProcessAction(action)
      },
      lastDeployedAction = lastAction.collect { case Deploy =>
        createProcessAction(Deploy)
      },
      json = jsonData,
      history = history,
      modelVersion = None
    )
  }

  def wrapWithDetailsForMigration(
      scenarioGraph: ScenarioGraph,
      name: ProcessName = ProcessTestData.sampleProcessName,
      isFragment: Boolean = false,
      validationResult: ValidationResult = ValidationResult.success
  ): ScenarioWithDetailsForMigrations = {
    ScenarioWithDetailsForMigrations(
      name = name,
      isArchived = false,
      isFragment = isFragment,
      processingType = Streaming,
      processCategory = TestCategories.Category1,
      scenarioGraph = Some(scenarioGraph),
      validationResult = Some(validationResult),
      history = None,
      modelVersion = None
    )
  }

  private def createEmptyJson(processingType: ProcessingType) = {
    val typeSpecificProperties = processingType match {
      case Streaming | Fraud => StreamMetaData()
      case _                 => throw new IllegalArgumentException(s"Unknown processing type: $processingType.")
    }

    ScenarioGraph(ProcessProperties(typeSpecificProperties), Nil, Nil)
  }

  lazy val sampleFragmentGraph: ScenarioGraph =
    createFragmentGraph(
      List(FragmentInputDefinition("input", List(FragmentParameter("in", FragmentClazzRef[String]))))
    )

  def createFragmentGraph(
      nodes: List[NodeData]
  ): ScenarioGraph =
    ScenarioGraph(ProcessProperties(FragmentSpecificData()), nodes, Nil)

  def createProcessAction(action: ProcessActionType): ProcessAction = ProcessAction(
    id = ProcessActionId(UUID.randomUUID()),
    processId = ProcessId(generateId()),
    processVersionId = VersionId(generateId()),
    createdAt = Instant.now(),
    performedAt = Instant.now(),
    user = "user",
    actionType = action,
    state = ProcessActionState.Finished,
    failureMessage = None,
    commentId = None,
    comment = None,
    buildInfo = Map.empty
  )

  private def generateId() = Math.abs(randomGenerator.nextLong())

}
